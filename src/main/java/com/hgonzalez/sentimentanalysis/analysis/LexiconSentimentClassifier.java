package com.hgonzalez.sentimentanalysis.analysis;

import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class LexiconSentimentClassifier implements SentimentClassifier {

    private static final Set<String> POSITIVE_WORDS = Set.of(
            "great", "love", "loved", "excellent", "amazing", "fast", "smooth",
            "awesome", "good", "best", "happy", "easy", "helpful");

    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "terrible", "slow", "delay", "delayed", "awful", "worst", "bad",
            "scam", "broken", "frustrating", "angry", "bug", "crash", "never");

    @Override
    public SentimentResult classify(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean positive = POSITIVE_WORDS.stream().anyMatch(lower::contains);
        boolean negative = NEGATIVE_WORDS.stream().anyMatch(lower::contains);

        SentimentLabel label;
        if (positive && negative) {
            label = SentimentLabel.MIXED;
        } else if (positive) {
            label = SentimentLabel.POSITIVE;
        } else if (negative) {
            label = SentimentLabel.NEGATIVE;
        } else {
            label = SentimentLabel.NEUTRAL;
        }

        return new SentimentResult(label, 0.5, List.of(), "Keyword-based fallback classification.", ClassifierKind.LEXICON_FALLBACK, "lexicon-v1");
    }
}
