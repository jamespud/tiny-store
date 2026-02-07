package com.github.spud.tinystore.infrastructure.security;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@ConditionalOnClass({SecurityFilterChain.class, HttpSecurity.class})
@ConditionalOnProperty(prefix = "tinystore.security.permit-all", name = "enabled", havingValue = "true")
public class PermitAllAutoConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain permitAllSecurityFilterChain(ObjectProvider<HttpSecurity> httpProvider) throws Exception {
    HttpSecurity http = httpProvider.getIfAvailable();
    if (http == null) {
      log.warn(
        "tinystore.security.permit-all.enabled=true but HttpSecurity is not available; registering an empty SecurityFilterChain"
      );
      return new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE, List.of());
    }

    log.info("tinystore.security.permit-all.enabled=true -> registering permit-all SecurityFilterChain");
    http
      .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
      .csrf(csrf -> csrf.disable())
      .formLogin(form -> form.disable())
      .httpBasic(basic -> basic.disable());
    return http.build();
  }
}
