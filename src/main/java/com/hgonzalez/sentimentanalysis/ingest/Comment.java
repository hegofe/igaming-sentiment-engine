package com.hgonzalez.sentimentanalysis.ingest;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("ingest.comment")
public record Comment(
        @Id UUID id,
        int source,
        String userId,
        String text,
        Instant occurredAt,
        Instant ingestedAt) {
}
