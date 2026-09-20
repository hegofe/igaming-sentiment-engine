CREATE SCHEMA IF NOT EXISTS analysis;

CREATE TABLE analysis.processed_message (
    event_id     uuid PRIMARY KEY,
    comment_id   uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);
