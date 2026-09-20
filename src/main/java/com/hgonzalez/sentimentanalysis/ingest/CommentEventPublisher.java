package com.hgonzalez.sentimentanalysis.ingest;

import com.hgonzalez.sentimentanalysis.config.RabbitConfig;
import com.hgonzalez.sentimentanalysis.contracts.CommentIngestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public CommentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = correlationData != null ? correlationData.getId() : "unknown";
                log.error("Publisher confirm failed for eventId={}: {}", id, cause);
            }
        });
    }

    public void publish(CommentIngestedEvent event) {
        String routingKey = "comment.ingested.v1." + event.source().name().toLowerCase();
        CorrelationData correlationData = new CorrelationData(event.eventId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.COMMENTS_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setMessageId(event.eventId());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData);
    }
}
