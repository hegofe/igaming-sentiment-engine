package com.hgonzalez.sentimentanalysis.analysis;

public interface SentimentClassifier {

    SentimentResult classify(String text);
}
