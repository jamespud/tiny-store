package com.github.spud.tinystore.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "`authorization`")
public class Authorization {

  @Id
  @Column
  private String id;
  private String registeredClientId;
  private String principalName;
  private String authorizationGrantType;
  @Column(length = 1000)
  private String authorizedScopes;
  @Column(length = 4000)
  private String attributes;
  @Column(length = 500)
  private String state;

  @Column(length = 4000)
  private String authorizationCodeValue;
  private Instant authorizationCodeIssuedAt;
  private Instant authorizationCodeExpiresAt;
  private String authorizationCodeMetadata;

  @Column(length = 4000)
  private String accessTokenValue;
  private Instant accessTokenIssuedAt;
  private Instant accessTokenExpiresAt;
  @Column(length = 2000)
  private String accessTokenMetadata;
  private String accessTokenType;
  @Column(length = 1000)
  private String accessTokenScopes;

  @Column(length = 4000)
  private String refreshTokenValue;
  private Instant refreshTokenIssuedAt;
  private Instant refreshTokenExpiresAt;
  @Column(length = 2000)
  private String refreshTokenMetadata;

  @Column(length = 4000)
  private String oidcIdTokenValue;
  private Instant oidcIdTokenIssuedAt;
  private Instant oidcIdTokenExpiresAt;
  @Column(length = 2000)
  private String oidcIdTokenMetadata;
  @Column(length = 2000)
  private String oidcIdTokenClaims;

  @Column(length = 4000)
  private String userCodeValue;
  private Instant userCodeIssuedAt;
  private Instant userCodeExpiresAt;
  @Column(length = 2000)
  private String userCodeMetadata;

  @Column(length = 4000)
  private String deviceCodeValue;
  private Instant deviceCodeIssuedAt;
  private Instant deviceCodeExpiresAt;
  @Column(length = 2000)
  private String deviceCodeMetadata;

}
