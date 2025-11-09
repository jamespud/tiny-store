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
@Table(name = "`client`")
public class Client {

  @Id
  private String id;
  private String clientId;
  private Instant clientIdIssuedAt;
  private String clientSecret;
  private Instant clientSecretExpiresAt;
  private String clientName;
  @Column(length = 1000)
  private String clientAuthenticationMethods;
  @Column(length = 1000)
  private String authorizationGrantTypes;
  @Column(length = 1000)
  private String redirectUris;
  @Column(length = 1000)
  private String postLogoutRedirectUris;
  @Column(length = 1000)
  private String scopes;
  @Column(length = 2000)
  private String clientSettings;
  @Column(length = 2000)
  private String tokenSettings;

  // Explicit getters and setters to satisfy compile-time references in JPA repositories
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public java.time.Instant getClientIdIssuedAt() {
    return clientIdIssuedAt;
  }

  public void setClientIdIssuedAt(java.time.Instant clientIdIssuedAt) {
    this.clientIdIssuedAt = clientIdIssuedAt;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public java.time.Instant getClientSecretExpiresAt() {
    return clientSecretExpiresAt;
  }

  public void setClientSecretExpiresAt(java.time.Instant clientSecretExpiresAt) {
    this.clientSecretExpiresAt = clientSecretExpiresAt;
  }

  public String getClientName() {
    return clientName;
  }

  public void setClientName(String clientName) {
    this.clientName = clientName;
  }

  public String getClientAuthenticationMethods() {
    return clientAuthenticationMethods;
  }

  public void setClientAuthenticationMethods(String clientAuthenticationMethods) {
    this.clientAuthenticationMethods = clientAuthenticationMethods;
  }

  public String getAuthorizationGrantTypes() {
    return authorizationGrantTypes;
  }

  public void setAuthorizationGrantTypes(String authorizationGrantTypes) {
    this.authorizationGrantTypes = authorizationGrantTypes;
  }

  public String getRedirectUris() {
    return redirectUris;
  }

  public void setRedirectUris(String redirectUris) {
    this.redirectUris = redirectUris;
  }

  public String getPostLogoutRedirectUris() {
    return postLogoutRedirectUris;
  }

  public void setPostLogoutRedirectUris(String postLogoutRedirectUris) {
    this.postLogoutRedirectUris = postLogoutRedirectUris;
  }

  public String getScopes() {
    return scopes;
  }

  public void setScopes(String scopes) {
    this.scopes = scopes;
  }

  public String getClientSettings() {
    return clientSettings;
  }

  public void setClientSettings(String clientSettings) {
    this.clientSettings = clientSettings;
  }

  public String getTokenSettings() {
    return tokenSettings;
  }

  public void setTokenSettings(String tokenSettings) {
    this.tokenSettings = tokenSettings;
  }

}
