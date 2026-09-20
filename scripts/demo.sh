#!/usr/bin/env bash
#
# Demo script for the eGaming Sentiment Engine.
# Requires: curl, jq.
#
# Usage:
#   ./scripts/demo.sh [--count N] [--base-url URL] [--data-file PATH]
#                      [--rabbitmq-url URL] [--rabbitmq-user U] [--rabbitmq-password P]
#                      [--ready-timeout SECONDS] [--drain-timeout SECONDS]
#
# --count 0 (the default) processes the full 300-comment corpus, which can take on the
# order of an hour with CPU-only LLM inference. Pass e.g. --count 15 for a fast demo.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="http://localhost:8080"
RABBITMQ_URL="http://localhost:15672"
RABBITMQ_USER="egaming"
RABBITMQ_PASSWORD="egaming"
DATA_FILE="$SCRIPT_DIR/../data/comments-300.ndjson"
COUNT=0
READY_TIMEOUT=120
DRAIN_TIMEOUT=3600

while [[ $# -gt 0 ]]; do
    case "$1" in
        --count) COUNT="$2"; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --data-file) DATA_FILE="$2"; shift 2 ;;
        --rabbitmq-url) RABBITMQ_URL="$2"; shift 2 ;;
        --rabbitmq-user) RABBITMQ_USER="$2"; shift 2 ;;
        --rabbitmq-password) RABBITMQ_PASSWORD="$2"; shift 2 ;;
        --ready-timeout) READY_TIMEOUT="$2"; shift 2 ;;
        --drain-timeout) DRAIN_TIMEOUT="$2"; shift 2 ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done

for bin in curl jq; do
    if ! command -v "$bin" >/dev/null 2>&1; then
        echo "This script requires '$bin' to be installed." >&2
        exit 1
    fi
done

wait_for_app_ready() {
    echo "Waiting for the app to report healthy at $BASE_URL/actuator/health ..."
    local elapsed=0
    while [[ $elapsed -lt $READY_TIMEOUT ]]; do
        local status
        status=$(curl -s -o /tmp/demo-health.json -w "%{http_code}" "$BASE_URL/actuator/health" || echo "000")
        if [[ "$status" == "200" ]] && [[ "$(jq -r '.status' /tmp/demo-health.json 2>/dev/null)" == "UP" ]]; then
            echo "App is healthy."
            return 0
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
    echo "App did not report healthy within ${READY_TIMEOUT}s. Is 'docker compose up -d' running and is the app started?" >&2
    exit 1
}

get_queue_depth() {
    local response
    response=$(curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" "$RABBITMQ_URL/api/queues/%2f/comments.analysis.q")
    local ready unacked
    ready=$(echo "$response" | jq -r '.messages_ready // 0')
    unacked=$(echo "$response" | jq -r '.messages_unacknowledged // 0')
    echo $((ready + unacked))
}

wait_for_queue_drain() {
    echo "Waiting for the analysis pipeline to drain (LLM classification is the slow step - this can take a while on CPU-only inference)..."
    # RabbitMQ's management API refreshes its stats on an interval, not in real time,
    # so a check made immediately after posting can read a stale "0" - settle first,
    # then require two consecutive zero readings before trusting it.
    sleep 5
    local elapsed=5
    local last_print=-1
    local consecutive_zero=0
    while [[ $elapsed -lt $DRAIN_TIMEOUT ]]; do
        local depth
        depth=$(get_queue_depth)
        if [[ "$depth" -eq 0 ]]; then
            consecutive_zero=$((consecutive_zero + 1))
            if [[ $consecutive_zero -ge 2 ]]; then
                echo "Queue drained after ${elapsed}s."
                return 0
            fi
        else
            consecutive_zero=0
        fi
        if [[ $((elapsed - last_print)) -ge 15 ]]; then
            echo "  ... still processing, $depth message(s) remaining (${elapsed}s elapsed)"
            last_print=$elapsed
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
    echo "Warning: queue did not fully drain within ${DRAIN_TIMEOUT}s - continuing anyway, some insight answers may reflect partial data." >&2
}

invoke_insight_query() {
    local question="$1"
    local filters_json="$2"   # "null" or a JSON object string

    local body
    body=$(jq -n --arg q "$question" --argjson filters "$filters_json" \
        '{question: $q} + (if $filters == null then {} else {filters: $filters} end)')

    echo ""
    echo "======================================================================"
    echo "Q: $question"
    echo "======================================================================"

    local response
    response=$(curl -s -X POST "$BASE_URL/api/v1/insights/query" -H "Content-Type: application/json" -d "$body")

    echo ""
    echo "Answer:"
    echo "  $(echo "$response" | jq -r '.answer')"

    echo ""
    local neg_pct top_aspects
    neg_pct=$(echo "$response" | jq -r '(.aggregates.negativeShare * 100 * 10 | round) / 10')
    top_aspects=$(echo "$response" | jq -r '[.aggregates.topAspects[] | (.aspect + "=" + (.count | tostring))] | join(", ")')
    echo "Aggregates: totalComments=$(echo "$response" | jq -r '.aggregates.totalComments') negativeShare=${neg_pct}% topAspects=[$top_aspects]"

    local citation_count
    citation_count=$(echo "$response" | jq '.citations | length')
    if [[ "$citation_count" -gt 0 ]]; then
        echo ""
        echo "Citations:"
        echo "$response" | jq -r '.citations[] | "  - [" + (.source | tostring) + "] \"" + .excerpt + "\" (id=" + .id + ")"'
    fi
}

# 1. Readiness
wait_for_app_ready

# 2. Load and post the demo corpus
if [[ ! -f "$DATA_FILE" ]]; then
    echo "Demo data file not found: $DATA_FILE" >&2
    exit 1
fi

if [[ "$COUNT" -gt 0 ]]; then
    LINES=$(head -n "$COUNT" "$DATA_FILE")
else
    LINES=$(cat "$DATA_FILE")
fi
LINE_COUNT=$(echo "$LINES" | grep -c . || true)

echo ""
echo "Posting $LINE_COUNT comment(s) to $BASE_URL/api/v1/comments ..."
accepted=0
duplicates=0
while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/comments" -H "Content-Type: application/json" -d "$line")
    if [[ "$status" == "200" ]]; then
        accepted=$((accepted + 1))
    elif [[ "$status" == "409" ]]; then
        duplicates=$((duplicates + 1))
    else
        echo "Unexpected status $status posting comment, aborting. Line: $line" >&2
        exit 1
    fi
done <<< "$LINES"
echo "Posted: $accepted accepted, $duplicates duplicate(s) (already ingested from a previous run)."

# 3. Wait for the async pipeline (classification + embedding) to finish
wait_for_queue_drain

# 4. Run insight queries that exploit the planted patterns in the corpus
invoke_insight_query "Why are players unhappy, and what should we do about it?" "null"

invoke_insight_query "What are players saying about withdrawal delays recently?" \
    '{"aspects":["WITHDRAWAL_DELAY"],"from":"2026-07-20","to":"2026-08-03"}'

invoke_insight_query "What do players think about the bonus wagering requirements?" \
    '{"aspects":["BONUS_WAGERING"]}'

echo ""
echo "Demo complete."
