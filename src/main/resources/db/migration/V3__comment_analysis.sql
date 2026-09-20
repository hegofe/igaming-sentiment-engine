CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE analysis.comment_analysis (
    comment_id      uuid PRIMARY KEY REFERENCES ingest.comment (id),
    sentiment       varchar(20) NOT NULL,
    confidence      numeric(4,3) NOT NULL,
    aspects         text[] NOT NULL DEFAULT '{}',
    rationale       text,
    classifier_kind varchar(20) NOT NULL,
    model_id        varchar(100),
    analysed_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_comment_analysis_sentiment ON analysis.comment_analysis (sentiment);
CREATE INDEX idx_comment_analysis_aspects ON analysis.comment_analysis USING gin (aspects);

CREATE TABLE analysis.comment_vectors (
    id        uuid PRIMARY KEY,
    content   text NOT NULL,
    metadata  jsonb NOT NULL DEFAULT '{}',
    embedding vector(768) NOT NULL
);

CREATE INDEX idx_comment_vectors_embedding ON analysis.comment_vectors
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_comment_vectors_metadata ON analysis.comment_vectors USING gin (metadata);
