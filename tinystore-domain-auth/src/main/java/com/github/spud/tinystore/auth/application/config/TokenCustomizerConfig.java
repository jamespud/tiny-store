package com.github.spud.tinystore.auth.application.config;

import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class TokenCustomizerConfig {

  @Value("${tinystore.auth.tokens.user.id-token-ttl:PT2H}")
  private Duration userIdTokenTtl;

  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
    return context -> {
      Authentication principal = context.getPrincipal();
      if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        Collection<? extends GrantedAuthority> authorities = principal != null
            ? principal.getAuthorities()
            : List.of();
        List<String> authorityValues = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
        List<String> roles = authorityValues.stream()
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring("ROLE_".length()))
            .collect(Collectors.toList());
        context.getClaims().claim("authorities", authorityValues);
        context.getClaims().claim("roles", roles);
        if (principal != null) {
          context.getClaims().subject(principal.getName());
        }
      }

      if (new OAuth2TokenType(OidcParameterNames.ID_TOKEN).equals(context.getTokenType())) {
        if (principal instanceof MallUser user) {
          context.getClaims().subject(user.getId().value());
          context.getClaims().claim("user_id", user.getId().value());
          context.getClaims().claim("rt_version", user.getRtVersion().value());
          context.getClaims().claim("status", user.getStatus().name());

          if (user.getUsername() != null) {
            context.getClaims().claim("nickname", user.getUsername());
          }
          if (user.getAvatar() != null) {
            context.getClaims().claim("avatar", user.getAvatar());
          }
          Set<String> scopes = context.getAuthorizedScopes();
          if (scopes.contains("user.phone")) {
            context.getClaims().claim("phone_number", user.getPhone());
          }
          if (scopes.contains("user.address")) {
            context.getClaims().claim("address_scope_granted", true);
          }
        } else if (principal != null) {
          context.getClaims().subject(principal.getName());
        }
        context.getClaims().expiresAt(Instant.now().plus(userIdTokenTtl));
      }
    };
  }
}
