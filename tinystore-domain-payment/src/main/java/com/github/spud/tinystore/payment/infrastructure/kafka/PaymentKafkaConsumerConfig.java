package com.github.spud.tinystore.payment.infrastructure.kafka;

import java.util.function.BiFunction;

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
import org.springframework.util.backoff.FixedBackOff;

/**
 * Payment 域 Kafka 消费配置：手动 ack + 有界重试 + DLT。
 *
 * <p>修复 C1：消费者方法声明了 {@link org.springframework.kafka.support.Acknowledgment}，
 * 但此前既没有 {@code spring.kafka.listener.ack-mode: manual}，也没有容器工厂，
 * 于是每条消息都在参数绑定阶段就抛
 * {@code No Acknowledgment available as an argument...}，重试耗尽后被静默丢弃——
 * 支付域 100% 消费不到订单事件。
 *
 * <p>DLT 语义（镜像 order/promotion 的配置）：
 * <ul>
 *   <li>毒消息（JSON 解析失败 / 参数不合法）立即进 DLT，不重试；</li>
 *   <li>业务或基础设施失败按 FixedBackOff 重试，达上限后进 DLT；</li>
 *   <li>DLT topic 命名：&lt;原 topic&gt;.DLT。</li>
 * </ul>
 */
@Configuration
public class PaymentKafkaConsumerConfig {

    @Value("${payment.kafka.consumer.retry.max-attempts:5}")
    private long maxAttempts;

    @Value("${payment.kafka.consumer.retry.backoff.interval-ms:1000}")
    private long retryIntervalMs;

    /**
     * 覆盖 Spring Boot 自动配置的 listener factory：手动 ack + 统一 DLT error handler。
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
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate, destinationResolver);
        // 计划要求：1s × 5 retries，然后 DLT。
        FixedBackOff backOff = new FixedBackOff(retryIntervalMs, maxAttempts);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // 毒消息（解析/字段失败）立即进 DLT，不重试。
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);
        return errorHandler;
    }
}
