package com.github.spud.tinystore.auth.application.config;

import java.time.Duration;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "tinystore.auth.outbox")
public class OutboxProperties {

	private boolean enabled = true;

	private Duration publishInterval = Duration.ofSeconds(5);

	private int batchSize = 100;

}

