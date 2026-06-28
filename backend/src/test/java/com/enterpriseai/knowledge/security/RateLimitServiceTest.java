package com.enterpriseai.knowledge.security;

import com.enterpriseai.knowledge.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {
    @Test
    void storesChatCounterInRedisAndAllowsRequestsWithinLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60"))).thenReturn(5L);
        RateLimitService service = service(redis);

        RateLimitService.Decision decision =
                service.consume("user@example.com", RateLimitService.Bucket.CHAT);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(5);
        assertThat(decision.count()).isEqualTo(5);
        verify(redis).execute(any(RedisScript.class), anyList(), eq("60"));
    }

    @Test
    void rejectsUploadOverLimitAndReturnsRedisTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("3600"))).thenReturn(3L);
        when(redis.getExpire(any(String.class), eq(TimeUnit.SECONDS))).thenReturn(900L);
        RateLimitService service = service(redis);

        RateLimitService.Decision decision =
                service.consume("user@example.com", RateLimitService.Bucket.DOCUMENT_UPLOAD);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(2);
        assertThat(decision.retryAfterSeconds()).isEqualTo(900);
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class)))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        RateLimitService.Decision decision =
                service(redis).consume("user@example.com", RateLimitService.Bucket.CHAT);

        assertThat(decision.allowed()).isTrue();
    }

    private RateLimitService service(StringRedisTemplate redis) {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:*")),
                new AppProperties.Jwt("test-secret-that-is-at-least-32-characters", 60_000),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Ai(
                        "openai", "", "https://api.openai.com/v1",
                        "chat", "embedding", 1536, "2024-10-21"),
                new AppProperties.Retrieval(5, 0.7, 0.3, 0.15),
                new AppProperties.RateLimit(5, 2),
                new AppProperties.BootstrapAdmin("", "", "")
        );
        return new RateLimitService(redis, properties);
    }
}
