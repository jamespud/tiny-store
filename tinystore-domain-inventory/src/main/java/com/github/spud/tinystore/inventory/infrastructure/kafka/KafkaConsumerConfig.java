package com.github.spud.tinystore.inventory.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.function.BiFunction;

/**
 * Kafka consumer config for inventory domain (rebuilt after Priority 1② deletion).
 * Manual ack + DefaultErrorHandler with exponential backoff + DLT.
 * Identical pattern to promotion domain's KafkaConsumerConfig.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${tinystore.kafka.consumer.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${tinystore.kafka.consumer.retry.backoff.initial-ms:500}")
    private long initialInterval;

    @Value("${tinystore.kafka.consumer.retry.backoff.multiplier:2.0}")
    private double multiplier;

    @Value("${tinystore.kafka.consumer.retry.backoff.max-ms:10000}")
    private long maxInterval;

    @Value("${tinystore.kafka.consumer.dlt.suffix:.DLT}")
    private String dltSuffix;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate));
        return factory;
    }

    private CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
            (record, ex) -> {
                String dltTopic = record.topic() + dltSuffix;
                return new TopicPartition(dltTopic, record.partition());
            };

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate, destinationResolver);

        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, multiplier);
        backOff.setMaxInterval(maxInterval);
        backOff.setMaxAttempts(maxAttempts);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
            JsonProcessingException.class,
            IllegalArgumentException.class
        );

        return errorHandler;
    }
}
