package com.hgonzalez.sentimentanalysis.analysis;

import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;

import java.util.List;

public record SentimentResult(
        SentimentLabel label,
        double confidence,
        List<Aspect> aspects,
        String rationale,
        ClassifierKind producedBy,
        String modelId) {
}
