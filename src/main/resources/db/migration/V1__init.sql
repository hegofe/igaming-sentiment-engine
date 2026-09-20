CREATE SCHEMA IF NOT EXISTS ingest;

CREATE TABLE ingest.comment (
    id           uuid PRIMARY KEY,
    source       smallint NOT NULL,
    user_id      varchar(128),
    text         varchar(2000) NOT NULL,
    occurred_at  timestamptz NOT NULL,
    ingested_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_comment_occurred_at ON ingest.comment (occurred_at);
CREATE INDEX idx_comment_user_id ON ingest.comment (user_id);
