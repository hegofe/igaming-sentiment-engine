package com.hgonzalez.sentimentanalysis.ingest.api;

import com.hgonzalez.sentimentanalysis.contracts.CommentSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CommentRequest(
        @NotNull
        CommentSource source,

        @NotNull
        UUID uuid,

        @Size(max = 128)
        String userId,

        @NotBlank
        @Size(max = 2000)
        String text,

        @NotNull
        @PastOrPresent
        Instant occurredAt) {
}
