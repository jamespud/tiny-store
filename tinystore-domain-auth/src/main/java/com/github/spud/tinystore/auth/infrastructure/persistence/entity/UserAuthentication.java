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
@Table(name = "`user_authentication`")
public class UserAuthentication {

  @Id
  @Column
  private String id;

  /* login username */
  @Column(unique = true, nullable = false)
  private String username;

  @Column(unique = true, nullable = false)
  private String phoneNumber;

  @Column(nullable = false)
  private String password;

  private boolean enabled;

  private long rtVersion;

  private Instant createdAt;

  private Instant updatedAt;
}
