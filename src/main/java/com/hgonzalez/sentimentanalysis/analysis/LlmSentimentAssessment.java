package com.hgonzalez.sentimentanalysis.analysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;

import java.util.List;

// No producedBy field here - the caller sets it after the call succeeds, never the model itself.
public record LlmSentimentAssessment(
        @JsonPropertyDescription("Overall sentiment of the comment")
        SentimentLabel label,

        @JsonPropertyDescription("Confidence in this classification, from 0.0 to 1.0")
        double confidence,

        @JsonPropertyDescription("Zero or more aspects the comment touches on, from the closed vocabulary")
        List<Aspect> aspects,

        @JsonPropertyDescription("One-sentence explanation of why this label and these aspects were chosen")
        String rationale) {
}
