# eGaming Sentiment Engine

Event-driven service that ingests player comments from an online betting/casino product, classifies their sentiment with a local LLM, and answers marketing questions over the corpus with a hybrid retrieval pipeline that grounds every number in SQL and every quote in a cited comment.

## Overview

- Comments come in through a single REST endpoint and are durably queued for analysis, so ingestion never blocks on a multi-second LLM call.
- Classification produces a label (`POSITIVE` / `NEGATIVE` / `NEUTRAL` / `MIXED`), a confidence score, and zero or more aspects from a closed vocabulary tuned to eGaming complaints (withdrawal delays, bonus wagering, odds value, KYC, and others), plus a one-sentence rationale.
- If the LLM is unreachable or returns something unusable, classification falls back to a keyword-based lexicon so the pipeline degrades instead of stalling or dropping the comment.
- Every classified comment is embedded and stored in pgvector alongside a structured `comment_analysis` row.
- Marketing questions are answered by combining exact SQL aggregates (volume, negative share, top aspects) with vector-retrieved evidence quotes. Numbers always come from SQL, never from the model, and every cited comment id is checked against what was actually retrieved before it's returned.

## Architecture

### Write path — ingest & analysis

```text
        Player Comment
   (Android / iOS / Web)
              |
              | POST /api/v1/comments
              v
        Spring Boot app
         /            \
        v              v
    PostgreSQL      RabbitMQ
  ingest.comment   egaming.comments
                    (topic exchange)
                         |
                         | comment.ingested.v1.*
                         v
               comments.analysis.q
                          |
              +-----------+-----------+
              |                       |
              | valid                 | poison / retries exhausted
              v                       v
        Spring Boot app         comments.analysis.dlq
     (RabbitMQ listener)
              |
              | classify + embed
              v
      Ollama (local LLM)
 qwen2.5:1.5b + nomic-embed-text
              |        \
              |         \ LLM unreachable / bad output
              |          v
              |     Lexicon fallback
              |         /
              | sentiment JSON + 768-dim vector
              v
          PostgreSQL
     /         |          \
comment_    processed_   comment_vectors
analysis     message      (pgvector, HNSW)
(label,     (idempotency)
 aspects)
```

### Read path — insight query

```text
      Marketing Analyst
              |
              | POST /api/v1/insights/query
              v
        Spring Boot app
        /                \
       / SQL aggregate     \ vector similarity search
      v                     v
comment_analysis        comment_vectors
exact numbers            relevant quotes
(volume, % negative,     (top-k, filtered)
 top aspects)
      \                     /
       \                   /
        v                 v
          Ollama (local LLM)
                 |
                 | grounded answer, numbers from SQL only
                 v
          InsightAnswer JSON
   (answer + citations + aggregates)
```

Two things worth calling out in these diagrams: the **DLQ branch** (failures are handled, not just the happy path) and the **split read path** — numbers come from SQL, prose comes from vector search, so the model has no way to invent a figure.

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 25, Spring Boot 4.1.1 |
| HTTP | Spring Web (`spring-boot-starter-webmvc`), Jakarta Bean Validation |
| Persistence | Spring Data JDBC, PostgreSQL, Flyway migrations |
| Messaging | RabbitMQ, Spring AMQP, spring-retry |
| LLM / embeddings | Spring AI 2.0.0 (`ChatClient`) over Ollama — `qwen2.5:1.5b` for chat, `nomic-embed-text` for 768-dim embeddings |
| Vector store | pgvector (`spring-ai-starter-vector-store-pgvector`), HNSW cosine index |
| Tests | JUnit 5, Testcontainers (`pgvector/pgvector:pg17`, RabbitMQ) |

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/comments` | Ingest one player comment |
| `POST` | `/api/v1/insights/query` | Ask a grounded question over the classified corpus |
| `GET` | `/actuator/health` | Liveness/readiness (Postgres + RabbitMQ) |

### Ingest a comment

```json
POST /api/v1/comments
{
  "source": 3,
  "uuid": "cfcec9b2-7669-4d9c-92f0-30356c061630",
  "userId": "user-653451b2",
  "text": "Great platform when everything works, frustrating when it doesn't.",
  "occurredAt": "2026-03-16T02:39:54Z"
}
```

- `source` is a fixed numeric code: `1` = `APP_ANDROID`, `2` = `APP_APPLE`, `3` = `WEB`.
- `uuid` is caller-supplied and doubles as the dedup key: an unseen `uuid` returns `200 OK` with an empty body, a repeated one returns `409 Conflict` with `{"duplicate": true}`.
- Validation failures return an RFC 7807 problem-detail body with per-field errors.

### Ask a question

```json
POST /api/v1/insights/query
{
  "question": "What are players saying about withdrawal delays recently?",
  "filters": {
    "aspects": ["WITHDRAWAL_DELAY"],
    "sentiments": ["NEGATIVE", "MIXED"],
    "from": "2026-07-20",
    "to": "2026-08-03"
  }
}
```

Returns:

```json
{
  "answer": "...",
  "citations": [
    { "id": "...", "source": "APP_ANDROID", "excerpt": "..." }
  ],
  "aggregates": {
    "totalComments": 20,
    "negativeShare": 0.85,
    "topAspects": [{ "aspect": "WITHDRAWAL_DELAY", "count": 20 }]
  }
}
```

`filters` is optional, and every field inside it is optional too. If narrative generation fails, the endpoint still returns real `aggregates` with an explanatory `answer` instead of failing the request.

## Running locally

### Prerequisites

- Docker Desktop (or another Docker engine) with Docker Compose
- JDK 25 to build and run outside a container — the Maven Wrapper is committed, so no local Maven install is needed
- A few GB of free disk for the Ollama models (`qwen2.5:1.5b` + `nomic-embed-text`), pulled automatically on first startup

### Quick start

```powershell
# 1. Start Postgres (pgvector), RabbitMQ, and Ollama; the models are pulled automatically
docker compose up -d

# 2. Run the app
.\mvnw.cmd spring-boot:run
```

```bash
# 1. Start Postgres (pgvector), RabbitMQ, and Ollama; the models are pulled automatically
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run
```

First startup pulls the Ollama models into a named volume via the `ollama-init` container, which can take a few minutes. `/actuator/health` reports `UP` once the app can reach both Postgres and RabbitMQ.

### Run the demo

Once the app is healthy, `scripts/demo.ps1` (PowerShell) or `scripts/demo.sh` (bash, requires `curl` and `jq`) posts the bundled 300-comment corpus (`data/comments-300.ndjson`), waits for the RabbitMQ analysis queue to drain, and runs three insight queries against the results.

```powershell
.\scripts\demo.ps1 -Count 15   # a fast subset; omit -Count for the full 300 comments
```

```bash
./scripts/demo.sh --count 15   # a fast subset; omit --count for the full 300 comments
```

Classification is CPU-bound Ollama inference, so a full 300-comment run can take on the order of an hour — `-Count`/`--count` exists to give a fast, real demonstration without committing to that.

### Ports

| Port | Service |
|---|---|
| 8080 | App (REST API, `/actuator/health`) |
| 5672 / 15672 | RabbitMQ (AMQP / management UI) |
| 5432 | PostgreSQL |
| 11434 | Ollama |

### Stop

```
docker compose down       # keep volumes (models, data)
docker compose down -v    # also remove volumes
```

## Testing

```
./mvnw test
```

Runs `SentimentAnalysisApplicationTests`, a Spring Boot context-load test backed by Testcontainers (`pgvector/pgvector:pg17`, RabbitMQ) via `@ServiceConnection` — it boots the full application against real Postgres and RabbitMQ instances with no manual container setup. There is no broader unit-test suite yet, and the LLM-dependent paths (sentiment classification, insight generation) are not covered by automated tests; they're verified by hand via `scripts/demo.ps1` / `demo.sh` against a live Ollama instance, which is stated here rather than implied.

## Design decisions

- **One Maven module, one process.** The ingest endpoint and the RabbitMQ listener run in the same Spring Boot application. RabbitMQ still sits between them as a real durable queue, so ingestion never blocks on classification — the separation that matters is enforced by the broker, not by deployment topology.
- **Manual acknowledgement, not auto-ack.** `CommentIngestedListener` only acks a message after it has been classified, embedded, and persisted — not after it has been deserialized. A crash mid-processing redelivers the message instead of silently losing it.
- **Retry, then dead-letter.** Transient failures retry with exponential backoff (`spring-retry`, 4 attempts, 1s–30s); an unsupported `schemaVersion` is explicitly non-retryable and dead-letters immediately. Either way, nothing disappears silently — it lands in `comments.analysis.dlq` for inspection.
- **Idempotency at two layers.** `ingest.comment` dedups on the caller-supplied `uuid` (`INSERT ... ON CONFLICT DO NOTHING`); `analysis.processed_message` dedups on `eventId`, so a RabbitMQ redelivery of an already-processed message is a safe no-op rather than a duplicate classification.
- **LLM first, lexicon as an availability fallback, not a quality one.** Player comments are sarcasm-heavy and often mix praise with complaint in the same sentence ("great, another 5-day *instant* withdrawal"); a keyword lexicon can't make that judgment or produce the aspect and rationale fields the insight layer depends on. The lexicon exists purely so the pipeline degrades instead of failing when the model is unreachable.
- **Hybrid retrieval so the model can't invent numbers.** `RagQueryService` computes aggregates with plain SQL and treats vector search purely as a source of illustrative quotes. The system prompt (`prompts/insight-system.st`) requires that any statistic come from the supplied aggregates, and any comment id the model cites is cross-checked against what was actually retrieved before it reaches the response — a hallucinated id is silently dropped rather than surfaced.
- **No chunking.** Comments are short, so each one is a single embedding. What is enriched instead is the embedded text itself — a contextual prefix (`"[{source} | {sentiment} | {aspects}] {text}"`) — so semantically similar comments cluster by source and sentiment as well as by wording.
