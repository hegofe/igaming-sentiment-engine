package com.hgonzalez.sentimentanalysis.rag;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.UUID;

// Raw shape the LLM fills in; the caller cross-checks citedCommentIds against
// what was actually retrieved before turning them into Citation records.
public record LlmInsightAssessment(
        @JsonPropertyDescription("The written answer to the question, grounded only in the supplied aggregates and evidence")
        String answer,

        @JsonPropertyDescription("Ids of evidence comments (from the supplied list only) that support this answer; empty if none")
        List<UUID> citedCommentIds) {
}
