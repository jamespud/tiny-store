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
@Table(name = "oauth2_authorization")
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

  // Explicit getters/setters to satisfy compile-time references (in addition to Lombok)
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRegisteredClientId() {
    return registeredClientId;
  }

  public void setRegisteredClientId(String registeredClientId) {
    this.registeredClientId = registeredClientId;
  }

  public String getPrincipalName() {
    return principalName;
  }

  public void setPrincipalName(String principalName) {
    this.principalName = principalName;
  }

  public String getAuthorizationGrantType() {
    return authorizationGrantType;
  }

  public void setAuthorizationGrantType(String authorizationGrantType) {
    this.authorizationGrantType = authorizationGrantType;
  }

  public String getAuthorizedScopes() {
    return authorizedScopes;
  }

  public void setAuthorizedScopes(String authorizedScopes) {
    this.authorizedScopes = authorizedScopes;
  }

  public String getAttributes() {
    return attributes;
  }

  public void setAttributes(String attributes) {
    this.attributes = attributes;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getAuthorizationCodeValue() {
    return authorizationCodeValue;
  }

  public Instant getAuthorizationCodeIssuedAt() {
    return authorizationCodeIssuedAt;
  }

  public Instant getAuthorizationCodeExpiresAt() {
    return authorizationCodeExpiresAt;
  }

  public String getAuthorizationCodeMetadata() {
    return authorizationCodeMetadata;
  }

  public void setAuthorizationCodeValue(String v) {
    this.authorizationCodeValue = v;
  }

  public void setAuthorizationCodeIssuedAt(Instant v) {
    this.authorizationCodeIssuedAt = v;
  }

  public void setAuthorizationCodeExpiresAt(Instant v) {
    this.authorizationCodeExpiresAt = v;
  }

  public void setAuthorizationCodeMetadata(String v) {
    this.authorizationCodeMetadata = v;
  }

  public String getAccessTokenValue() {
    return accessTokenValue;
  }

  public Instant getAccessTokenIssuedAt() {
    return accessTokenIssuedAt;
  }

  public Instant getAccessTokenExpiresAt() {
    return accessTokenExpiresAt;
  }

  public String getAccessTokenMetadata() {
    return accessTokenMetadata;
  }

  public String getAccessTokenType() {
    return accessTokenType;
  }

  public String getAccessTokenScopes() {
    return accessTokenScopes;
  }

  public void setAccessTokenValue(String v) {
    this.accessTokenValue = v;
  }

  public void setAccessTokenIssuedAt(Instant v) {
    this.accessTokenIssuedAt = v;
  }

  public void setAccessTokenExpiresAt(Instant v) {
    this.accessTokenExpiresAt = v;
  }

  public void setAccessTokenMetadata(String v) {
    this.accessTokenMetadata = v;
  }

  public void setAccessTokenType(String v) {
    this.accessTokenType = v;
  }

  public void setAccessTokenScopes(String v) {
    this.accessTokenScopes = v;
  }

  public String getRefreshTokenValue() {
    return refreshTokenValue;
  }

  public Instant getRefreshTokenIssuedAt() {
    return refreshTokenIssuedAt;
  }

  public Instant getRefreshTokenExpiresAt() {
    return refreshTokenExpiresAt;
  }

  public String getRefreshTokenMetadata() {
    return refreshTokenMetadata;
  }

  public void setRefreshTokenValue(String v) {
    this.refreshTokenValue = v;
  }

  public void setRefreshTokenIssuedAt(Instant v) {
    this.refreshTokenIssuedAt = v;
  }

  public void setRefreshTokenExpiresAt(Instant v) {
    this.refreshTokenExpiresAt = v;
  }

  public void setRefreshTokenMetadata(String v) {
    this.refreshTokenMetadata = v;
  }

  public String getOidcIdTokenValue() {
    return oidcIdTokenValue;
  }

  public Instant getOidcIdTokenIssuedAt() {
    return oidcIdTokenIssuedAt;
  }

  public Instant getOidcIdTokenExpiresAt() {
    return oidcIdTokenExpiresAt;
  }

  public String getOidcIdTokenMetadata() {
    return oidcIdTokenMetadata;
  }

  public String getOidcIdTokenClaims() {
    return oidcIdTokenClaims;
  }

  public void setOidcIdTokenValue(String v) {
    this.oidcIdTokenValue = v;
  }

  public void setOidcIdTokenIssuedAt(Instant v) {
    this.oidcIdTokenIssuedAt = v;
  }

  public void setOidcIdTokenExpiresAt(Instant v) {
    this.oidcIdTokenExpiresAt = v;
  }

  public void setOidcIdTokenMetadata(String v) {
    this.oidcIdTokenMetadata = v;
  }

  public void setOidcIdTokenClaims(String v) {
    this.oidcIdTokenClaims = v;
  }

  public String getUserCodeValue() {
    return userCodeValue;
  }

  public Instant getUserCodeIssuedAt() {
    return userCodeIssuedAt;
  }

  public Instant getUserCodeExpiresAt() {
    return userCodeExpiresAt;
  }

  public String getUserCodeMetadata() {
    return userCodeMetadata;
  }

  public void setUserCodeValue(String v) {
    this.userCodeValue = v;
  }

  public void setUserCodeIssuedAt(Instant v) {
    this.userCodeIssuedAt = v;
  }

  public void setUserCodeExpiresAt(Instant v) {
    this.userCodeExpiresAt = v;
  }

  public void setUserCodeMetadata(String v) {
    this.userCodeMetadata = v;
  }

  public String getDeviceCodeValue() {
    return deviceCodeValue;
  }

  public Instant getDeviceCodeIssuedAt() {
    return deviceCodeIssuedAt;
  }

  public Instant getDeviceCodeExpiresAt() {
    return deviceCodeExpiresAt;
  }

  public String getDeviceCodeMetadata() {
    return deviceCodeMetadata;
  }

  public void setDeviceCodeValue(String v) {
    this.deviceCodeValue = v;
  }

  public void setDeviceCodeIssuedAt(Instant v) {
    this.deviceCodeIssuedAt = v;
  }

  public void setDeviceCodeExpiresAt(Instant v) {
    this.deviceCodeExpiresAt = v;
  }

  public void setDeviceCodeMetadata(String v) {
    this.deviceCodeMetadata = v;
  }

}
