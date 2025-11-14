package com.nas_backend.repository;

import com.nas_backend.model.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, String> {

    // Find token by its ID (the token string itself)
    Optional<UserToken> findByToken(String token);

    // Find all tokens for a given username
    List<UserToken> findByUsername(String username);

    // Find all expired tokens
    List<UserToken> findByExpirationTimeBefore(Instant now);
}
