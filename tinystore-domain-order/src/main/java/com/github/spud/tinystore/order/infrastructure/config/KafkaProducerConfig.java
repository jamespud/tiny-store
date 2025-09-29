package com.github.spud.tinystore.order.infrastructure.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置
 * 配置 Kafka 生产者用于发布领域事件
 */
@Configuration
public class KafkaProducerConfig {

	@Value("${spring.kafka.bootstrap-servers:localhost:9092}")
	private String bootstrapServers;

	@Value("${spring.kafka.producer.retries:3}")
	private Integer retries;

	@Value("${spring.kafka.producer.batch-size:16384}")
	private Integer batchSize;

	@Value("${spring.kafka.producer.linger-ms:1}")
	private Integer lingerMs;

	@Value("${spring.kafka.producer.buffer-memory:33554432}")
	private Long bufferMemory;

	@Bean
	public ProducerFactory<String, String> producerFactory() {
		Map<String, Object> configProps = new HashMap<>();

		// 基础配置
		configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

		// 性能配置
		configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
		configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
		configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
		configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

		// 可靠性配置
		configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 等待所有副本确认
		configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 启用幂等性
		configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

		// 压缩配置
		configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

		// 超时配置
		configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
		configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

		return new DefaultKafkaProducerFactory<>(configProps);
	}

	@Bean
	public KafkaTemplate<String, String> kafkaTemplate() {
		KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());

		// 设置默认主题
		template.setDefaultTopic("tinystore.order.general");

		return template;
	}
}