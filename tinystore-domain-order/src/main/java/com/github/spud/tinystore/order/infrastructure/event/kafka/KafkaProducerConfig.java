package com.github.spud.tinystore.order.infrastructure.event.kafka;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Kafka 生产者配置
 */
@Configuration
@EnableKafka
public class KafkaProducerConfig {
    // Spring Boot 3.5.0 自动配置 Kafka，无需显式配置 ProducerFactory/KafkaTemplate
    // 通过 application.yml 中的 spring.kafka.bootstrap-servers 等配置即可
}
