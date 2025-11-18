package com.nas_backend.config;

import com.nas_backend.service.system.RateLimitingService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.lang.NonNull;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private final RateLimitingService rateLimitingService;

    public RateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String clientIp = getClientIP(request);
        String uri = request.getRequestURI();

        Bucket bucket;

        // Select bucket based on endpoint
        if (uri.startsWith("/api/auth/login")) {
            bucket = rateLimitingService.resolveLoginBucket(clientIp);
        } else if (uri.startsWith("/api/")) {
            // Rest of API
            bucket = rateLimitingService.resolveGeneralBucket(clientIp);
        } else {
            // Static resources etc. - allow freely
            return true;
        }

        // Attempt to consume a token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Success - allow the request
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        } else {
            // Limit exceeded - block!
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            logger.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, uri);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            response.getWriter().write("Too many requests. You are being rate limited. Try again in " + waitForRefill + " seconds.");

            return false; // Block further processing
        }
    }

    // Helper method to extract client IP address
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}