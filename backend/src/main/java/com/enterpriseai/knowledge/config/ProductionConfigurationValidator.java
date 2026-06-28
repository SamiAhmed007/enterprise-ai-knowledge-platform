package com.enterpriseai.knowledge.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ProductionConfigurationValidator implements ApplicationRunner {
    static final String DEVELOPMENT_JWT_SECRET =
            "development-only-secret-key-change-me-123456789";

    private final AppProperties properties;
    private final Environment environment;

    public ProductionConfigurationValidator(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String deployment = environment.getProperty("APP_ENVIRONMENT", "development");
        if (!"production".equalsIgnoreCase(deployment)) return;

        if (DEVELOPMENT_JWT_SECRET.equals(properties.jwt().secret())) {
            throw new IllegalStateException("Production cannot use the development JWT secret");
        }
        if (properties.cors().allowedOriginPatterns().stream()
                .anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1")
                        || origin.equals("*"))) {
            throw new IllegalStateException("Production CORS origins must be explicit deployment origins");
        }
        String adminPassword = properties.bootstrapAdmin().password();
        if (adminPassword != null && Arrays.asList(
                "admin12345", "change-this-admin-password").contains(adminPassword)) {
            throw new IllegalStateException("Production cannot use the example administrator password");
        }
    }
}
