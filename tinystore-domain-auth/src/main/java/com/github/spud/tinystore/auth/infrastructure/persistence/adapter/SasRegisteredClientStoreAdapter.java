package com.github.spud.tinystore.auth.infrastructure.persistence.adapter;

import com.github.spud.tinystore.auth.application.port.out.RegisteredClientStorePort;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Authorization Server JDBC 的 RegisteredClient 存储适配器。 复用框架的
 * RegisteredClientRepository/JdbcTemplate 来持久化。
 */
@Component
public class SasRegisteredClientStoreAdapter implements RegisteredClientStorePort {

  private final RegisteredClientRepository repository;

  public SasRegisteredClientStoreAdapter(RegisteredClientRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(RegisteredClient registeredClient) {
    repository.save(registeredClient);
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    return repository.findByClientId(clientId);
  }
}