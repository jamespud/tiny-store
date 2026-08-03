package com.github.spud.tinystore.order.infrastructure.kafka;

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
 * Kafka consumer 配置：手动 ack + DLT 错误处理（镜像 promotion 的 KafkaConsumerConfig）。
 * <p>
 * DLT 语义：
 * - 毒消息（IllegalArgumentException）立即进 DLT
 * - 业务异常 exponential backoff 重试，达上限后进 DLT
 * - DLT topic 命名：&lt;原 topic&gt;.DLT
 */
@Configuration
public class OrderKafkaConsumerConfig {


    @Value("${order.kafka.consumer.retry.backoff.initial-ms:500}")
    private long initialInterval;

    @Value("${order.kafka.consumer.retry.backoff.multiplier:2.0}")
    private double multiplier;

    @Value("${order.kafka.consumer.retry.backoff.max-ms:10000}")
    private long maxInterval;

    /**
     * 覆盖 Spring Boot 自动配置的 listener factory：手动 ack + 统一 DLT error handler。
     * PromotionAckConsumer 的 @KafkaListener 由此 factory 驱动。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(orderErrorHandler(kafkaTemplate));
        return factory;
    }

    @Bean
    public CommonErrorHandler orderErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition());
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, destinationResolver);
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, multiplier);
        backOff.setMaxInterval(maxInterval);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // 毒消息（解析/字段失败）立即进 DLT，不重试
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);
        return errorHandler;
    }
}
