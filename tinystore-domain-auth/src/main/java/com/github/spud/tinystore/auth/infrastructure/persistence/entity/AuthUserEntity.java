package com.github.spud.tinystore.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserEntity {

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "phone", nullable = false, unique = true, length = 32)
  private String phone;

  @Column(name = "password", nullable = false, length = 255)
  private String password;

  @Column(name = "account_status", nullable = false)
  private Integer accountStatus;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
