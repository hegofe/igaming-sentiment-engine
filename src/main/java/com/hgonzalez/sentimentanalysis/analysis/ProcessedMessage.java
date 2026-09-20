package com.hgonzalez.sentimentanalysis.analysis;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("analysis.processed_message")
public record ProcessedMessage(
        @Id UUID eventId,
        UUID commentId,
        Instant processedAt) {
}
