package com.hgonzalez.sentimentanalysis.rag;

import jakarta.validation.constraints.NotBlank;

public record InsightQueryRequest(
        @NotBlank
        String question,

        InsightQueryFilters filters) {
}
