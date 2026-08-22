package com.github.spud.tinystore.promotion.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Kafka 消费者配置
 * <p>
 * 核心配置：
 * - 手动 ack 模式（ack-mode: MANUAL）
 * - 统一的 DLT（Dead Letter Topic）错误处理
 * <p>
 * DLT 错误处理：
 * - 可重试异常：exponential backoff 后重试，达上限后进 DLT
 * - 不可重试异常（毒消息）：立即进 DLT
 * - DLT topic 命名：<原 topic>.DLT
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

    /**
     * 促销事件发布器（回执事件发往 ack topic，默认 tinystore.promotion.general）。
     * ObjectMapper 使用 Spring Boot 自动配置的 bean。
     */
    @Bean
    public PromotionEventPublisher promotionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                           ObjectMapper objectMapper,
                                                           @Value("${promotion.kafka.topic.ack-events:tinystore.promotion.general}") String topic) {
        return new PromotionEventPublisher(kafkaTemplate, objectMapper, topic);
    }

    /**
     * 配置 Kafka listener 容器工厂
     * <p>
     * 设置手动 ack 模式，消费者方法必须调用 Acknowledgment.acknowledge() 才能提交 offset。
     * 挂载统一错误处理器，将重试耗尽/不可重试的消息发布到 DLT。
     *
     * @param consumerFactory Spring Boot 自动配置的 ConsumerFactory
     * @param kafkaTemplate Kafka 生产者模板（用于 DLT 发布）
     * @return Kafka listener 容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 设置手动 ack 模式
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 挂载统一错误处理器
        factory.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate));

        return factory;
    }

    /**
     * 创建 Kafka 错误处理器
     * <p>
     * 策略：
     * - 可重试异常：按 exponential backoff 重试，达上限后进 DLT
     * - 不可重试异常：立即进 DLT（毒消息，如 JSON 解析/字段校验失败）
     * <p>
     * 重试次数控制：
     * DefaultErrorHandler 通过 BackOff 策略控制重试；这里用
     * ExponentialBackOff.setMaxAttempts(maxAttempts) 把重试精确限制为 max-attempts 次，
     * 达到上限后交由 DeadLetterPublishingRecoverer 进 DLT。
     * 若只想固定间隔退避，可改用 FixedBackOff(interval, maxAttempts)。
     *
     * @param kafkaTemplate Kafka 生产者模板
     * @return 统一错误处理器
     */
    private CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // DLT 发布器：将失败消息路由到 <原topic>.DLT
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
            (record, ex) -> {
                String originalTopic = record.topic();
                String dltTopic = originalTopic + dltSuffix;
                return new TopicPartition(dltTopic, record.partition());
            };

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            destinationResolver
        );

        // Exponential backoff 配置（initialInterval=500, multiplier=2.0, maxInterval=10000）
        // 序列：500, 1000, 2000, 4000, 8000, ... 封顶 10000。
        // setMaxAttempts(maxAttempts) 精确限制重试次数为 max-attempts，达上限后进 DLT。
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, multiplier);
        backOff.setMaxInterval(maxInterval);
        backOff.setMaxAttempts(maxAttempts);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 配置不可重试异常（毒消息）
        errorHandler.addNotRetryableExceptions(
            JsonProcessingException.class,           // JSON 解析失败
            IllegalArgumentException.class           // 字段校验/缺失
        );

        return errorHandler;
    }
}
