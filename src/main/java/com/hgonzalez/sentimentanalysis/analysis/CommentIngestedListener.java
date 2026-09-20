package com.hgonzalez.sentimentanalysis.analysis;

import com.hgonzalez.sentimentanalysis.config.RabbitConfig;
import com.hgonzalez.sentimentanalysis.contracts.CommentIngestedEvent;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CommentIngestedListener {

    private static final Logger log = LoggerFactory.getLogger(CommentIngestedListener.class);

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final RetryTemplate retryTemplate;
    private final ProcessedMessageRepository processedMessageRepository;
    private final SentimentClassifier sentimentClassifier;
    private final CommentEmbeddingService commentEmbeddingService;

    public CommentIngestedListener(RetryTemplate commentProcessingRetryTemplate,
                                    ProcessedMessageRepository processedMessageRepository,
                                    SentimentClassifier sentimentClassifier,
                                    CommentEmbeddingService commentEmbeddingService) {
        this.retryTemplate = commentProcessingRetryTemplate;
        this.processedMessageRepository = processedMessageRepository;
        this.sentimentClassifier = sentimentClassifier;
        this.commentEmbeddingService = commentEmbeddingService;
    }

    @RabbitListener(queues = RabbitConfig.ANALYSIS_QUEUE, containerFactory = "manualAckContainerFactory")
    public void onCommentIngested(CommentIngestedEvent event,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            if (event.schemaVersion() != CURRENT_SCHEMA_VERSION) {
                throw new NonRetryableMessageException("Unsupported schemaVersion: " + event.schemaVersion());
            }

            UUID eventId = UUID.fromString(event.eventId());
            int claimed = processedMessageRepository.insertIfAbsent(eventId, event.commentId());
            if (claimed == 0) {
                log.info("Skipping already-processed message {} (comment {})", eventId, event.commentId());
            } else {
                retryTemplate.execute(context -> {
                    process(event);
                    return null;
                });
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Failed to process comment {}: {}", event.commentId(), ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void process(CommentIngestedEvent event) {
        SentimentResult result = sentimentClassifier.classify(event.text());
        log.info("Classified comment {} (source={}): label={} confidence={} aspects={} producedBy={}",
                event.commentId(), event.source(), result.label(), result.confidence(),
                result.aspects(), result.producedBy());

        commentEmbeddingService.embedAndStore(
                event.commentId(), event.source(), event.userId(), event.occurredAt(), event.text(), result);
    }
}
