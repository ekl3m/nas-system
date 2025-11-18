package com.nas_backend.service.system;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    // Cache: IP Address -> Bucket
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    // Methods to create new buckets with specific limits

    // Login rate limiting: Max 5 login attempts per 1 minute
    private Bucket createNewLoginBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofMinutes(1))
                .build();

        return Bucket.builder().addLimit(limit).build();
    }

    // General API: Max 200 requests per 1 minute
    private Bucket createNewGeneralBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(200)
                .refillGreedy(200, Duration.ofMinutes(1))
                .build();

        return Bucket.builder().addLimit(limit).build();
    }

    // Helper methods to get or create buckets for a given IP address

    public Bucket resolveLoginBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, k -> createNewLoginBucket());
    }

    public Bucket resolveGeneralBucket(String ip) {
        return generalBuckets.computeIfAbsent(ip, k -> createNewGeneralBucket());
    }
}