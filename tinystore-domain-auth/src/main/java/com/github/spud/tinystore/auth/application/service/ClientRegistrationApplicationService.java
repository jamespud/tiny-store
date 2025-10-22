package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.RegisteredClientDto;
import com.github.spud.tinystore.auth.application.dto.RegisteredClientRequest;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientRegistrationApplicationService {

  private final RegisteredClientRepository registeredClientRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogPort auditLogPort;

  public ClientRegistrationApplicationService(
      RegisteredClientRepository registeredClientRepository,
      PasswordEncoder passwordEncoder,
      AuditLogPort auditLogPort) {
    this.registeredClientRepository = registeredClientRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogPort = auditLogPort;
  }

  public RegisteredClientDto register(RegisteredClientRequest request, String registrationToken) {
    // Strict validation
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

    List<String> scopes = request.scopes() == null ? List.of("openid", "user.profile") : request.scopes();
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
    registeredClientRepository.save(rc);

    auditLogPort.append(AuditEvent.success(null, null, clientId,
        "CLIENT_REGISTER", Set.copyOf(scopes), null, null, authMethod.getValue()));

    return new RegisteredClientDto(clientId, request.clientName(), request.redirectUris(),
        request.grantTypes(), scopes, authMethod.getValue(), clientSecret);
  }

  public RegisteredClientDto get(String clientId) {
    RegisteredClient rc = registeredClientRepository.findByClientId(clientId);
    if (rc == null) {
      throw new IllegalArgumentException("client not found");
    }
    List<String> redirectUris = rc.getRedirectUris().stream().collect(Collectors.toList());
    List<String> grantTypes = rc.getAuthorizationGrantTypes().stream()
        .map(AuthorizationGrantType::getValue)
        .collect(Collectors.toList());
    List<String> scopes = rc.getScopes().stream().collect(Collectors.toList());
    return new RegisteredClientDto(rc.getClientId(), rc.getClientName(), redirectUris, grantTypes,
        scopes, rc.getClientAuthenticationMethods().iterator().next().getValue(), null);
  }

  private void validate(RegisteredClientRequest request, String registrationToken) {
    if (!StringUtils.hasText(registrationToken)) {
      throw new IllegalArgumentException("registration token required");
    }
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