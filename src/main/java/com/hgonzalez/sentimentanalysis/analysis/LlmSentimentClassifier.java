package com.hgonzalez.sentimentanalysis.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Primary
public class LlmSentimentClassifier implements SentimentClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmSentimentClassifier.class);

    private final ChatClient chatClient;
    private final LexiconSentimentClassifier fallback;
    private final String modelId;

    public LlmSentimentClassifier(ChatClient.Builder chatClientBuilder,
                                   @Value("classpath:prompts/sentiment-system.st") Resource systemPrompt,
                                   @Value("${spring.ai.ollama.chat.options.model}") String modelId,
                                   LexiconSentimentClassifier fallback) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.fallback = fallback;
        this.modelId = modelId;
    }

    @Override
    public SentimentResult classify(String text) {
        try {
            LlmSentimentAssessment assessment = chatClient.prompt()
                    .options(ChatOptions.builder().temperature(0.0))
                    .user(text)
                    .call()
                    .entity(LlmSentimentAssessment.class);

            if (assessment == null) {
                throw new IllegalStateException("LLM returned no result");
            }

            return new SentimentResult(
                    assessment.label(), assessment.confidence(), assessment.aspects(),
                    assessment.rationale(), ClassifierKind.LLM, modelId);
        } catch (Exception ex) {
            log.warn("LLM classification failed, falling back to lexicon: {}", ex.getMessage());
            return fallback.classify(text);
        }
    }
}
