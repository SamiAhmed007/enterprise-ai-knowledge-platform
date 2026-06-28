package com.enterpriseai.knowledge.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectionConfigTest {
    private final RedisConnectionConfig config = new RedisConnectionConfig();

    @Test
    void createsPlaintextPasswordlessConnectionForLocalDevelopment() {
        LettuceConnectionFactory factory = (LettuceConnectionFactory)
                config.redisConnectionFactory("redis", 6379, "", false);

        assertThat(factory.getHostName()).isEqualTo("redis");
        assertThat(factory.getPort()).isEqualTo(6379);
        assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isFalse();
        assertThat(factory.getClientConfiguration().isUseSsl()).isFalse();
    }

    @Test
    void createsAuthenticatedTlsConnectionForUpstash() {
        LettuceConnectionFactory factory = (LettuceConnectionFactory)
                config.redisConnectionFactory(
                        "renewed-mantis-12345.upstash.io",
                        6379,
                        "upstash-secret",
                        true);

        assertThat(factory.getHostName()).isEqualTo("renewed-mantis-12345.upstash.io");
        assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isTrue();
        assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
    }
}
