package com.github.spud.tinystore.auth.interfaces.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 开发环境内存管理员账号，便于 E2E 管理端操作（例如撤销令牌）。
 */
@Configuration
@Profile("dev")
public class SecurityDevAdminConfig {

  @Bean
  public InMemoryUserDetailsManager adminUserDetailsManager() {
    UserDetails admin = User
        .withUsername("admin")
        .password("{noop}admin")
        .roles("ADMIN")
        .build();
    return new InMemoryUserDetailsManager(admin);
  }
}