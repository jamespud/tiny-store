package com.github.spud.tinystore.auth.application.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@ConfigurationProperties(prefix = "tinystore.auth.outbox")
@Validated
public class OutboxProperties {

  private boolean enabled = true;

  private Duration publishInterval = Duration.ofSeconds(5);

  private int batchSize = 100;

}
