package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.config.DynamicRegistrationProperties;
import com.github.spud.tinystore.auth.application.dto.RegisteredClientDto;
import com.github.spud.tinystore.auth.application.dto.RegisteredClientRequest;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.RegisteredClientStorePort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientRegistrationApplicationService {

  private final RegisteredClientStorePort registeredClientStore;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogPort auditLogPort;
  private final DynamicRegistrationProperties dynamicProps;

  public ClientRegistrationApplicationService(
      RegisteredClientStorePort registeredClientStore,
      PasswordEncoder passwordEncoder,
      AuditLogPort auditLogPort,
      DynamicRegistrationProperties dynamicProps) {
    this.registeredClientStore = registeredClientStore;
    this.passwordEncoder = passwordEncoder;
    this.auditLogPort = auditLogPort;
    this.dynamicProps = dynamicProps;
  }

  public RegisteredClientDto register(RegisteredClientRequest request, String registrationToken) {
    // 开关与令牌校验
    if (!dynamicProps.isEnabled()) {
      throw new IllegalStateException("dynamic registration disabled");
    }
    validateToken(registrationToken);

    // 严格校验
    validate(request, registrationToken);

    String clientId = UUID.randomUUID().toString();
    ClientAuthenticationMethod authMethod = resolveAuthMethod(request.tokenEndpointAuthMethod());

    RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(clientId)
        .clientName(request.clientName())
        .clientAuthenticationMethod(authMethod);

    for (String gt : request.grantTypes()) {
      builder.authorizationGrantType(resolveGrantType(gt));
    }
    for (String uri : request.redirectUris()) {
      builder.redirectUri(uri);
    }

    List<String> scopes =
        request.scopes() == null ? List.of("openid", "user.profile") : request.scopes();
    scopes.forEach(builder::scope);

    ClientSettings.Builder cs = ClientSettings.builder()
        .requireAuthorizationConsent(true);
    // Require PKCE for public/native clients
    if (ClientAuthenticationMethod.NONE.equals(authMethod)) {
      cs.requireProofKey(true);
    }

    builder.clientSettings(cs.build());
    builder.tokenSettings(TokenSettings.builder().build());

    String clientSecret = null;
    if (!ClientAuthenticationMethod.NONE.equals(authMethod)) {
      // Generate secret for confidential clients
      clientSecret = UUID.randomUUID().toString();
      builder.clientSecret(passwordEncoder.encode(clientSecret));
    }

    RegisteredClient rc = builder.build();
    registeredClientStore.save(rc);

    auditLogPort.append(AuditEvent.success(null, null, clientId,
        "CLIENT_REGISTER", Set.copyOf(scopes), null, null, authMethod.getValue()));

    return new RegisteredClientDto(clientId, request.clientName(), request.redirectUris(),
        request.grantTypes(), scopes, authMethod.getValue(), clientSecret);
  }

  public RegisteredClientDto get(String clientId) {
    RegisteredClient rc = registeredClientStore.findByClientId(clientId);
    if (rc == null) {
      throw new IllegalArgumentException("client not found");
    }
    List<String> redirectUris = new ArrayList<>(rc.getRedirectUris());
    List<String> grantTypes = rc.getAuthorizationGrantTypes().stream()
        .map(AuthorizationGrantType::getValue)
        .collect(Collectors.toList());
    List<String> scopes = new ArrayList<>(rc.getScopes());
    return new RegisteredClientDto(rc.getClientId(), rc.getClientName(), redirectUris, grantTypes,
        scopes, rc.getClientAuthenticationMethods().iterator().next().getValue(), null);
  }

  private void validate(RegisteredClientRequest request, String registrationToken) {
    if (!StringUtils.hasText(request.clientName())) {
      throw new IllegalArgumentException("client_name required");
    }
    if (request.redirectUris() == null || request.redirectUris().isEmpty()) {
      throw new IllegalArgumentException("redirect_uris required");
    }
    // Redirect URIs must be HTTPS and exact-match
    for (String uri : request.redirectUris()) {
      try {
        URI parsed = URI.create(uri);
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
          throw new IllegalArgumentException("redirect_uri must be https: " + uri);
        }
      } catch (Exception ex) {
        throw new IllegalArgumentException("invalid redirect_uri: " + uri);
      }
    }
    if (request.grantTypes() == null || request.grantTypes().isEmpty()) {
      throw new IllegalArgumentException("grant_types required");
    }
    // Allowed grant types
    Set<String> allowed = Set.of("authorization_code", "refresh_token", "client_credentials");
    for (String gt : request.grantTypes()) {
      if (!allowed.contains(gt)) {
        throw new IllegalArgumentException("unsupported grant type: " + gt);
      }
    }
    // Scope whitelist
    Set<String> scopeAllowed = Set.of("openid", "user.profile", "user.phone", "user.address",
        "user.follow", "user.payment", "svc.inventory.read", "svc.inventory.write",
        "svc.order.read", "svc.order.write", "svc.admin");
    if (request.scopes() != null) {
      for (String sc : request.scopes()) {
        if (!scopeAllowed.contains(sc)) {
          throw new IllegalArgumentException("unsupported scope: " + sc);
        }
      }
    }
  }

  private void validateToken(String registrationToken) {
    if (dynamicProps.isRequireToken()) {
      if (!StringUtils.hasText(registrationToken)) {
        throw new IllegalArgumentException("registration token required");
      }
      List<String> allowed = dynamicProps.getAllowedTokens();
      if (allowed != null && !allowed.isEmpty() && !allowed.contains(registrationToken)) {
        throw new IllegalArgumentException("registration token not allowed");
      }
    }
  }

  private static AuthorizationGrantType resolveGrantType(String gt) {
    return switch (gt) {
      case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
      case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
      case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
      default -> throw new IllegalArgumentException("unsupported grant type: " + gt);
    };
  }

  private static ClientAuthenticationMethod resolveAuthMethod(String m) {
    return switch (m) {
      case "client_secret_basic" -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
      case "client_secret_post" -> ClientAuthenticationMethod.CLIENT_SECRET_POST;
      case "none" -> ClientAuthenticationMethod.NONE;
      default -> throw new IllegalArgumentException("unsupported token_endpoint_auth_method: " + m);
    };
  }
}