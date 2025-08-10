package com.github.spud.tinystore.domain.security;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "oauth2_authorization_consent", schema = "security")
public class Oauth2AuthorizationConsent {

	@EmbeddedId
	private Oauth2AuthorizationConsentId id;

	@MapsId("registeredClientId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registered_client_id", nullable = false)
	private OAuth2RegisteredClient registeredClient;

	@NotNull
	@Column(name = "authorities", nullable = false)
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> authorities;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	public Oauth2AuthorizationConsentId getId() {
		return id;
	}

	public void setId(Oauth2AuthorizationConsentId id) {
		this.id = id;
	}

	public OAuth2RegisteredClient getRegisteredClient() {
		return registeredClient;
	}

	public void setRegisteredClient(OAuth2RegisteredClient registeredClient) {
		this.registeredClient = registeredClient;
	}

	public Map<String, Object> getAuthorities() {
		return authorities;
	}

	public void setAuthorities(Map<String, Object> authorities) {
		this.authorities = authorities;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	@Embeddable
	public static class Oauth2AuthorizationConsentId implements Serializable {

		private static final long serialVersionUID = -2093937567046260026L;
		@NotNull
		@Column(name = "registered_client_id", nullable = false)
		private UUID registeredClientId;

		@NotNull
		@Column(name = "principal_name", nullable = false, length = Integer.MAX_VALUE)
		private String principalName;

		public UUID getRegisteredClientId() {
			return registeredClientId;
		}

		public void setRegisteredClientId(UUID registeredClientId) {
			this.registeredClientId = registeredClientId;
		}

		public String getPrincipalName() {
			return principalName;
		}

		public void setPrincipalName(String principalName) {
			this.principalName = principalName;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
				return false;
			}
			Oauth2AuthorizationConsentId entity = (Oauth2AuthorizationConsentId) o;
			return Objects.equals(this.registeredClientId, entity.registeredClientId) &&
				Objects.equals(this.principalName, entity.principalName);
		}

		@Override
		public int hashCode() {
			return Objects.hash(registeredClientId, principalName);
		}

	}
}