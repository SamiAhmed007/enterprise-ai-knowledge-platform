package com.enterpriseai.knowledge.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {
    @Test
    void rejectsDevelopmentSecretInProduction() {
        AppProperties properties = properties(
                ProductionConfigurationValidator.DEVELOPMENT_JWT_SECRET,
                List.of("https://knowledge.example.com"),
                "strong-admin-password");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENVIRONMENT", "production");

        assertThatThrownBy(() ->
                new ProductionConfigurationValidator(properties, environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development JWT secret");
    }

    @Test
    void acceptsExplicitProductionSecurityConfiguration() {
        AppProperties properties = properties(
                "production-secret-with-more-than-thirty-two-characters",
                List.of("https://knowledge.example.com"),
                "strong-admin-password");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENVIRONMENT", "production");

        assertThatCode(() ->
                new ProductionConfigurationValidator(properties, environment).run(null))
                .doesNotThrowAnyException();
    }

    private AppProperties properties(String secret, List<String> origins, String adminPassword) {
        return new AppProperties(
                new AppProperties.Cors(origins),
                new AppProperties.Jwt(secret, 86_400_000),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Ai(
                        "openai", "", "https://api.openai.com/v1",
                        "chat", "embedding", 1536, "2024-10-21"),
                new AppProperties.Retrieval(5, 0.7, 0.3, 0.15),
                new AppProperties.RateLimit(20, 30),
                new AppProperties.BootstrapAdmin("admin@example.com", adminPassword, "Admin")
        );
    }
}
