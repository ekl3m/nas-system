package com.nas_backend.model.security;

import java.time.Instant;

public class UserToken {
    private final String username;
    private final String token;
    private final Instant createdAt;
    private final long ttlSeconds;

    public UserToken(String username, String token, long ttlSeconds) {
        this.username = username;
        this.token = token;
        this.createdAt = Instant.now();
        this.ttlSeconds = ttlSeconds;
    }

    // Getters

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Other methods

    public boolean isExpired() {
        return ttlSeconds > 0 && Instant.now().isAfter(createdAt.plusSeconds(ttlSeconds));
    }
}
