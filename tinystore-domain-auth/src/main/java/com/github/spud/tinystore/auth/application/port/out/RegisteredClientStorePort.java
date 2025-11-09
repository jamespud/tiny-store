package com.github.spud.tinystore.auth.application.port.out;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * DDD 端口：客户端注册信息存储抽象。 用于在应用层与具体存储实现解耦（JDBC/JPA/或外部系统）。
 */
public interface RegisteredClientStorePort {

  void save(RegisteredClient registeredClient);

  RegisteredClient findByClientId(String clientId);
}