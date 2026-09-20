package com.hgonzalez.sentimentanalysis.rag;

import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;

import java.time.LocalDate;
import java.util.List;

public record InsightQueryFilters(
        List<SentimentLabel> sentiments,
        List<Aspect> aspects,
        LocalDate from,
        LocalDate to) {
}
