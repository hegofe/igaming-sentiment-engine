package com.hgonzalez.sentimentanalysis.rag;

import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;

import java.time.Instant;
import java.util.List;

public record InsightFilters(
        List<SentimentLabel> sentiments,
        List<Aspect> aspects,
        Instant from,
        Instant to) {
}
