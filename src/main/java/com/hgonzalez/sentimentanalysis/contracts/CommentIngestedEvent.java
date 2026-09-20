package com.hgonzalez.sentimentanalysis.contracts;

import java.time.Instant;
import java.util.UUID;

public record CommentIngestedEvent(
        String eventId,
        int schemaVersion,
        UUID commentId,
        CommentSource source,
        String userId,
        String text,
        Instant occurredAt) {
}
