package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AiClientTest {
    private final AiClient client = new AiClient(properties(), RestClient.builder(), new ObjectMapper());

    @Test
    void createsStableLocalEmbeddingsWhenApiKeyIsMissing() {
        List<Double> first = client.embedding("Enterprise retention policy");
        List<Double> second = client.embedding("Enterprise retention policy");

        assertThat(first).hasSize(32).containsExactlyElementsOf(second);
        double norm = Math.sqrt(first.stream().mapToDouble(value -> value * value).sum());
        assertThat(norm).isCloseTo(1.0, within(0.000001));
    }

    @Test
    void returnsActionableMessageWhenGenerationIsNotConfigured() {
        assertThat(client.answer("Question", "Context"))
                .contains("OPENAI_API_KEY")
                .contains("citations");
    }

    @Test
    void streamsAndReassemblesLocalFallback() {
        StringBuilder answer = new StringBuilder();

        client.streamAnswer("Question", "Context", answer::append, () -> false);

        assertThat(answer).contains("OPENAI_API_KEY").contains("citations");
    }

    @Test
    void stopsLocalStreamWhenCancelled() {
        AtomicInteger checks = new AtomicInteger();

        assertThatThrownBy(() -> client.streamAnswer(
                "Question", "Context", ignored -> {}, () -> checks.incrementAndGet() > 1))
                .isInstanceOf(StreamCancelledException.class);
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:*")),
                new AppProperties.Jwt("test-secret-that-is-at-least-32-characters", 60_000),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Ai(
                        "openai", "", "https://api.openai.com/v1",
                        "chat", "embedding", 32, "2024-10-21"),
                new AppProperties.Retrieval(5, 0.7, 0.3, 0.15),
                new AppProperties.RateLimit(20, 30),
                new AppProperties.BootstrapAdmin("", "", "")
        );
    }
}
