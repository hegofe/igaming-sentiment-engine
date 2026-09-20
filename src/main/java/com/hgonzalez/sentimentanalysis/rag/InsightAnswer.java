package com.hgonzalez.sentimentanalysis.rag;

import java.util.List;

public record InsightAnswer(
        String answer,
        List<Citation> citations,
        Aggregates aggregates) {
}
