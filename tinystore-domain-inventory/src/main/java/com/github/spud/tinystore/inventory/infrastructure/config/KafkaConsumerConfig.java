package com.github.spud.tinystore.inventory.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka 消费者配置
 * <p>
 * 核心配置：
 * - 手动 ack 模式（ack-mode: MANUAL）
 * - 配合 application.yml 中的 enable-auto-commit: false
 * <p>
 * 手动 ack 的意义：
 * - 业务处理成功后才确认消息
 * - 异常时不 ack，让 Kafka 重试（避免消息丢失）
 * - 幂等保证重试安全
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * 配置 Kafka listener 容器工厂
     * <p>
     * 设置手动 ack 模式，消费者方法必须调用 Acknowledgment.acknowledge() 才能提交 offset。
     *
     * @param consumerFactory Spring Boot 自动配置的 ConsumerFactory
     * @return Kafka listener 容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 设置手动 ack 模式
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}
