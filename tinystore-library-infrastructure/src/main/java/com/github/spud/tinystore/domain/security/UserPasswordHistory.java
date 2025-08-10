package com.github.spud.tinystore.domain.security;

import com.github.spud.tinystore.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "user_password_history", schema = "security")
public class UserPasswordHistory extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    // Getters and Setters
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
