package com.enterpriseai.knowledge.security;

import com.enterpriseai.knowledge.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
            Long.class);

    private final StringRedisTemplate redis;
    private final AppProperties.RateLimit config;

    public RateLimitService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.config = properties.rateLimit();
    }

    public Decision consume(String username, Bucket bucket) {
        int limit = bucket == Bucket.CHAT
                ? config.chatRequestsPerMinute()
                : config.documentUploadsPerHour();
        Duration window = bucket == Bucket.CHAT ? Duration.ofMinutes(1) : Duration.ofHours(1);
        String key = "rate-limit:" + bucket.key + ":" + hash(username);

        try {
            Long count = redis.execute(
                    INCREMENT_WITH_EXPIRY,
                    List.of(key),
                    Long.toString(window.toSeconds()));
            if (count == null || count <= limit) {
                return Decision.allowed(limit, count == null ? 0 : count);
            }
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = ttl == null || ttl < 1 ? window.toSeconds() : ttl;
            return Decision.rejected(limit, count, retryAfter);
        } catch (RuntimeException exception) {
            // AI requests remain available during a Redis outage; monitoring should alert on this warning.
            log.warn("Rate limiting unavailable for {} requests; allowing request", bucket.key, exception);
            return Decision.allowed(limit, 0);
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public enum Bucket {
        CHAT("chat"),
        DOCUMENT_UPLOAD("document-upload");

        private final String key;

        Bucket(String key) {
            this.key = key;
        }
    }

    public record Decision(boolean allowed, int limit, long count, long retryAfterSeconds) {
        static Decision allowed(int limit, long count) {
            return new Decision(true, limit, count, 0);
        }

        static Decision rejected(int limit, long count, long retryAfterSeconds) {
            return new Decision(false, limit, count, retryAfterSeconds);
        }
    }
}
