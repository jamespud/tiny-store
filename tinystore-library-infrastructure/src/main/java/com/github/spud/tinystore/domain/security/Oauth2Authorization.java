package com.github.spud.tinystore.domain.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "oauth2_authorization", schema = "security")
public class Oauth2Authorization {

	@Id
	@ColumnDefault("uuid_generate_v4()")
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "registered_client_id", nullable = false)
	private OAuth2RegisteredClient registeredClient;

	@NotNull
	@Column(name = "principal_name", nullable = false, length = Integer.MAX_VALUE)
	private String principalName;

	@NotNull
	@Column(name = "authorization_grant_type", nullable = false, length = Integer.MAX_VALUE)
	private String authorizationGrantType;

	@Column(name = "authorized_scopes")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> authorizedScopes;

	@Column(name = "attributes")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> attributes;

	@Column(name = "state", length = Integer.MAX_VALUE)
	private String state;

	@Column(name = "authorization_code_value", length = Integer.MAX_VALUE)
	private String authorizationCodeValue;

	@Column(name = "authorization_code_issued_at")
	private OffsetDateTime authorizationCodeIssuedAt;

	@Column(name = "authorization_code_expires_at")
	private OffsetDateTime authorizationCodeExpiresAt;

	@Column(name = "authorization_code_metadata")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> authorizationCodeMetadata;

	@Column(name = "access_token_value", length = Integer.MAX_VALUE)
	private String accessTokenValue;

	@Column(name = "access_token_issued_at")
	private OffsetDateTime accessTokenIssuedAt;

	@Column(name = "access_token_expires_at")
	private OffsetDateTime accessTokenExpiresAt;

	@Column(name = "access_token_metadata")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> accessTokenMetadata;

	@Column(name = "access_token_type", length = Integer.MAX_VALUE)
	private String accessTokenType;

	@Column(name = "access_token_scopes")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> accessTokenScopes;

	@Column(name = "oidc_id_token_value", length = Integer.MAX_VALUE)
	private String oidcIdTokenValue;

	@Column(name = "oidc_id_token_issued_at")
	private OffsetDateTime oidcIdTokenIssuedAt;

	@Column(name = "oidc_id_token_expires_at")
	private OffsetDateTime oidcIdTokenExpiresAt;

	@Column(name = "oidc_id_token_metadata")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> oidcIdTokenMetadata;

	@Column(name = "refresh_token_value", length = Integer.MAX_VALUE)
	private String refreshTokenValue;

	@Column(name = "refresh_token_issued_at")
	private OffsetDateTime refreshTokenIssuedAt;

	@Column(name = "refresh_token_expires_at")
	private OffsetDateTime refreshTokenExpiresAt;

	@Column(name = "refresh_token_metadata")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> refreshTokenMetadata;

	@Column(name = "user_code_value", length = Integer.MAX_VALUE)
	private String userCodeValue;

	@Column(name = "user_code_issued_at")
	private OffsetDateTime userCodeIssuedAt;

	@Column(name = "user_code_expires_at")
	private OffsetDateTime userCodeExpiresAt;

	@Column(name = "user_code_metadata")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> userCodeMetadata;

	@Column(name = "device_code_value", length = Integer.MAX_VALUE)
	private String deviceCodeValue;

	@Column(name = "device_code_issued_at")
	private OffsetDateTime deviceCodeIssuedAt;

	@Column(name = "device_code_expires_at")
	private OffsetDateTime deviceCodeExpiresAt;

	@Column(name = "device_code_metadata")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> deviceCodeMetadata;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public OAuth2RegisteredClient getRegisteredClient() {
		return registeredClient;
	}

	public void setRegisteredClient(OAuth2RegisteredClient registeredClient) {
		this.registeredClient = registeredClient;
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

	public Map<String, Object> getAuthorizedScopes() {
		return authorizedScopes;
	}

	public void setAuthorizedScopes(Map<String, Object> authorizedScopes) {
		this.authorizedScopes = authorizedScopes;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
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

	public void setAuthorizationCodeValue(String authorizationCodeValue) {
		this.authorizationCodeValue = authorizationCodeValue;
	}

	public OffsetDateTime getAuthorizationCodeIssuedAt() {
		return authorizationCodeIssuedAt;
	}

	public void setAuthorizationCodeIssuedAt(OffsetDateTime authorizationCodeIssuedAt) {
		this.authorizationCodeIssuedAt = authorizationCodeIssuedAt;
	}

	public OffsetDateTime getAuthorizationCodeExpiresAt() {
		return authorizationCodeExpiresAt;
	}

	public void setAuthorizationCodeExpiresAt(OffsetDateTime authorizationCodeExpiresAt) {
		this.authorizationCodeExpiresAt = authorizationCodeExpiresAt;
	}

	public Map<String, Object> getAuthorizationCodeMetadata() {
		return authorizationCodeMetadata;
	}

	public void setAuthorizationCodeMetadata(Map<String, Object> authorizationCodeMetadata) {
		this.authorizationCodeMetadata = authorizationCodeMetadata;
	}

	public String getAccessTokenValue() {
		return accessTokenValue;
	}

	public void setAccessTokenValue(String accessTokenValue) {
		this.accessTokenValue = accessTokenValue;
	}

	public OffsetDateTime getAccessTokenIssuedAt() {
		return accessTokenIssuedAt;
	}

	public void setAccessTokenIssuedAt(OffsetDateTime accessTokenIssuedAt) {
		this.accessTokenIssuedAt = accessTokenIssuedAt;
	}

	public OffsetDateTime getAccessTokenExpiresAt() {
		return accessTokenExpiresAt;
	}

	public void setAccessTokenExpiresAt(OffsetDateTime accessTokenExpiresAt) {
		this.accessTokenExpiresAt = accessTokenExpiresAt;
	}

	public Map<String, Object> getAccessTokenMetadata() {
		return accessTokenMetadata;
	}

	public void setAccessTokenMetadata(Map<String, Object> accessTokenMetadata) {
		this.accessTokenMetadata = accessTokenMetadata;
	}

	public String getAccessTokenType() {
		return accessTokenType;
	}

	public void setAccessTokenType(String accessTokenType) {
		this.accessTokenType = accessTokenType;
	}

	public Map<String, Object> getAccessTokenScopes() {
		return accessTokenScopes;
	}

	public void setAccessTokenScopes(Map<String, Object> accessTokenScopes) {
		this.accessTokenScopes = accessTokenScopes;
	}

	public String getOidcIdTokenValue() {
		return oidcIdTokenValue;
	}

	public void setOidcIdTokenValue(String oidcIdTokenValue) {
		this.oidcIdTokenValue = oidcIdTokenValue;
	}

	public OffsetDateTime getOidcIdTokenIssuedAt() {
		return oidcIdTokenIssuedAt;
	}

	public void setOidcIdTokenIssuedAt(OffsetDateTime oidcIdTokenIssuedAt) {
		this.oidcIdTokenIssuedAt = oidcIdTokenIssuedAt;
	}

	public OffsetDateTime getOidcIdTokenExpiresAt() {
		return oidcIdTokenExpiresAt;
	}

	public void setOidcIdTokenExpiresAt(OffsetDateTime oidcIdTokenExpiresAt) {
		this.oidcIdTokenExpiresAt = oidcIdTokenExpiresAt;
	}

	public Map<String, Object> getOidcIdTokenMetadata() {
		return oidcIdTokenMetadata;
	}

	public void setOidcIdTokenMetadata(Map<String, Object> oidcIdTokenMetadata) {
		this.oidcIdTokenMetadata = oidcIdTokenMetadata;
	}

	public String getRefreshTokenValue() {
		return refreshTokenValue;
	}

	public void setRefreshTokenValue(String refreshTokenValue) {
		this.refreshTokenValue = refreshTokenValue;
	}

	public OffsetDateTime getRefreshTokenIssuedAt() {
		return refreshTokenIssuedAt;
	}

	public void setRefreshTokenIssuedAt(OffsetDateTime refreshTokenIssuedAt) {
		this.refreshTokenIssuedAt = refreshTokenIssuedAt;
	}

	public OffsetDateTime getRefreshTokenExpiresAt() {
		return refreshTokenExpiresAt;
	}

	public void setRefreshTokenExpiresAt(OffsetDateTime refreshTokenExpiresAt) {
		this.refreshTokenExpiresAt = refreshTokenExpiresAt;
	}

	public Map<String, Object> getRefreshTokenMetadata() {
		return refreshTokenMetadata;
	}

	public void setRefreshTokenMetadata(Map<String, Object> refreshTokenMetadata) {
		this.refreshTokenMetadata = refreshTokenMetadata;
	}

	public String getUserCodeValue() {
		return userCodeValue;
	}

	public void setUserCodeValue(String userCodeValue) {
		this.userCodeValue = userCodeValue;
	}

	public OffsetDateTime getUserCodeIssuedAt() {
		return userCodeIssuedAt;
	}

	public void setUserCodeIssuedAt(OffsetDateTime userCodeIssuedAt) {
		this.userCodeIssuedAt = userCodeIssuedAt;
	}

	public OffsetDateTime getUserCodeExpiresAt() {
		return userCodeExpiresAt;
	}

	public void setUserCodeExpiresAt(OffsetDateTime userCodeExpiresAt) {
		this.userCodeExpiresAt = userCodeExpiresAt;
	}

	public Map<String, Object> getUserCodeMetadata() {
		return userCodeMetadata;
	}

	public void setUserCodeMetadata(Map<String, Object> userCodeMetadata) {
		this.userCodeMetadata = userCodeMetadata;
	}

	public String getDeviceCodeValue() {
		return deviceCodeValue;
	}

	public void setDeviceCodeValue(String deviceCodeValue) {
		this.deviceCodeValue = deviceCodeValue;
	}

	public OffsetDateTime getDeviceCodeIssuedAt() {
		return deviceCodeIssuedAt;
	}

	public void setDeviceCodeIssuedAt(OffsetDateTime deviceCodeIssuedAt) {
		this.deviceCodeIssuedAt = deviceCodeIssuedAt;
	}

	public OffsetDateTime getDeviceCodeExpiresAt() {
		return deviceCodeExpiresAt;
	}

	public void setDeviceCodeExpiresAt(OffsetDateTime deviceCodeExpiresAt) {
		this.deviceCodeExpiresAt = deviceCodeExpiresAt;
	}

	public Map<String, Object> getDeviceCodeMetadata() {
		return deviceCodeMetadata;
	}

	public void setDeviceCodeMetadata(Map<String, Object> deviceCodeMetadata) {
		this.deviceCodeMetadata = deviceCodeMetadata;
	}

}