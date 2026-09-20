package com.hgonzalez.sentimentanalysis;

import org.springframework.boot.SpringApplication;

public class TestSentimentAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.from(SentimentAnalysisApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
