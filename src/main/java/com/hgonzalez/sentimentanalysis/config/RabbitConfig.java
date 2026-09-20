package com.hgonzalez.sentimentanalysis.config;

import com.hgonzalez.sentimentanalysis.analysis.NonRetryableMessageException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String COMMENTS_EXCHANGE = "egaming.comments";
    public static final String COMMENTS_DEAD_LETTER_EXCHANGE = "egaming.comments.dlx";
    public static final String ANALYSIS_QUEUE = "comments.analysis.q";
    public static final String ANALYSIS_DEAD_LETTER_QUEUE = "comments.analysis.dlq";
    public static final String ANALYSIS_ROUTING_PATTERN = "comment.ingested.v1.*";

    @Bean
    public TopicExchange commentsExchange() {
        return new TopicExchange(COMMENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange commentsDeadLetterExchange() {
        return new TopicExchange(COMMENTS_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue analysisQueue() {
        return QueueBuilder.durable(ANALYSIS_QUEUE)
                .deadLetterExchange(COMMENTS_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ANALYSIS_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Queue analysisDeadLetterQueue() {
        return QueueBuilder.durable(ANALYSIS_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding analysisQueueBinding() {
        return BindingBuilder.bind(analysisQueue()).to(commentsExchange()).with(ANALYSIS_ROUTING_PATTERN);
    }

    @Bean
    public Binding analysisDeadLetterBinding() {
        return BindingBuilder.bind(analysisDeadLetterQueue())
                .to(commentsDeadLetterExchange())
                .with(ANALYSIS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(8);
        factory.setConcurrentConsumers(4);
        return factory;
    }

    @Bean
    public RetryTemplate commentProcessingRetryTemplate() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(3.0);
        backOffPolicy.setMaxInterval(30000);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                4, Map.of(NonRetryableMessageException.class, false), true, true);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);
        return retryTemplate;
    }
}
