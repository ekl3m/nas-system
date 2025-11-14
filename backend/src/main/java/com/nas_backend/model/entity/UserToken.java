package com.nas_backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @Column(nullable = false, unique = true)
    private String token; // Token (UUID) will be the primary key

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant expirationTime;

    // No-argument constructor required by JPA
    public UserToken() {
    }

    public UserToken(String username, String token, long ttlSeconds) {
        this.username = username;
        this.token = token;
        this.expirationTime = Instant.now().plusSeconds(ttlSeconds);
    }

    // Simple method to check if token is expired
    public boolean isExpired() {
        return Instant.now().isAfter(this.expirationTime);
    }

    // Getters

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    // Setters

    public void setToken(String token) {
        this.token = token;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setExpirationTime(Instant expirationTime) {
        this.expirationTime = expirationTime;
    }
}