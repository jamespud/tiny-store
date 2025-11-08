package com.github.spud.tinystore.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "`authorizationConsent`")
@IdClass(AuthorizationConsent.AuthorizationConsentId.class)
public class AuthorizationConsent {

  @Id
  private String registeredClientId;
  @Id
  private String principalName;
  @Column(length = 1000)
  private String authorities;

  // Explicit getters and setters to satisfy compile-time references in JPA services
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

  public String getAuthorities() {
    return authorities;
  }

  public void setAuthorities(String authorities) {
    this.authorities = authorities;
  }

  @Setter
  @Getter
  public static class AuthorizationConsentId implements Serializable {

    private String registeredClientId;
    private String principalName;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AuthorizationConsentId that = (AuthorizationConsentId) o;
      return registeredClientId.equals(that.registeredClientId) && principalName.equals(
          that.principalName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(registeredClientId, principalName);
    }
  }
}