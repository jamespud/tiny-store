package com.github.spud.tinystore.domain.security;

import com.github.spud.tinystore.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "oauth2_registered_client", schema = "security")
public class OAuth2RegisteredClient extends BaseEntity {
    
    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;
    
    @Column(name = "client_id_issued_at", nullable = false)
    private LocalDateTime clientIdIssuedAt = LocalDateTime.now();
    
    @Column(name = "client_secret")
    private String clientSecret;
    
    @Column(name = "client_secret_expires_at")
    private LocalDateTime clientSecretExpiresAt;
    
    @Column(name = "client_name", nullable = false)
    private String clientName;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_authentication_methods", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> clientAuthenticationMethods;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorization_grant_types", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> authorizationGrantTypes;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redirect_uris", columnDefinition = "jsonb")
    private Map<String, Object> redirectUris;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_logout_redirect_uris", columnDefinition = "jsonb")
    private Map<String, Object> postLogoutRedirectUris;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> scopes;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_settings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> clientSettings;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_settings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> tokenSettings;
    
    // Getters and Setters
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public LocalDateTime getClientIdIssuedAt() {
        return clientIdIssuedAt;
    }
    
    public void setClientIdIssuedAt(LocalDateTime clientIdIssuedAt) {
        this.clientIdIssuedAt = clientIdIssuedAt;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public LocalDateTime getClientSecretExpiresAt() {
        return clientSecretExpiresAt;
    }
    
    public void setClientSecretExpiresAt(LocalDateTime clientSecretExpiresAt) {
        this.clientSecretExpiresAt = clientSecretExpiresAt;
    }
    
    public String getClientName() {
        return clientName;
    }
    
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    
    public Map<String, Object> getClientAuthenticationMethods() {
        return clientAuthenticationMethods;
    }
    
    public void setClientAuthenticationMethods(Map<String, Object> clientAuthenticationMethods) {
        this.clientAuthenticationMethods = clientAuthenticationMethods;
    }
    
    public Map<String, Object> getAuthorizationGrantTypes() {
        return authorizationGrantTypes;
    }
    
    public void setAuthorizationGrantTypes(Map<String, Object> authorizationGrantTypes) {
        this.authorizationGrantTypes = authorizationGrantTypes;
    }
    
    public Map<String, Object> getRedirectUris() {
        return redirectUris;
    }
    
    public void setRedirectUris(Map<String, Object> redirectUris) {
        this.redirectUris = redirectUris;
    }
    
    public Map<String, Object> getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }
    
    public void setPostLogoutRedirectUris(Map<String, Object> postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }
    
    public Map<String, Object> getScopes() {
        return scopes;
    }
    
    public void setScopes(Map<String, Object> scopes) {
        this.scopes = scopes;
    }
    
    public Map<String, Object> getClientSettings() {
        return clientSettings;
    }
    
    public void setClientSettings(Map<String, Object> clientSettings) {
        this.clientSettings = clientSettings;
    }
    
    public Map<String, Object> getTokenSettings() {
        return tokenSettings;
    }
    
    public void setTokenSettings(Map<String, Object> tokenSettings) {
        this.tokenSettings = tokenSettings;
    }
}
