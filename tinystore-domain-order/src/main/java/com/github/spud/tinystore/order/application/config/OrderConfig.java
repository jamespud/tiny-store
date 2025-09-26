package com.github.spud.tinystore.order.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单模块配置
 *
 * @author Spud
 * @date 2025/8/16
 */
@Configuration
public class OrderConfig {

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		return mapper;
	}
}
