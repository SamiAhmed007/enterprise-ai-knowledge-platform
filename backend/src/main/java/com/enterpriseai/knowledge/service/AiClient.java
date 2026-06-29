package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class AiClient {
    private final AppProperties.Ai config;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public AiClient(AppProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.config = properties.ai();
        this.client = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<Double> embedding(String text) {
        if (!configured()) return deterministicEmbedding(text);

        Map<String, Object> body = new HashMap<>();
        body.put("input", text);
        body.put("model", config.embeddingModel());
        if (!isAzure()) body.put("dimensions", config.embeddingDimensions());

        JsonNode response = request(embeddingUrl(), body);
        List<Double> values = new ArrayList<>();
        response.path("data").path(0).path("embedding").forEach(node -> values.add(node.asDouble()));
        if (values.size() != config.embeddingDimensions()) {
            throw new AiProviderException("Embedding API returned " + values.size()
                    + " dimensions; expected " + config.embeddingDimensions());
        }
        return values;
    }

    public String answer(String question, String context) {
        if (!configured()) {
            return "AI credentials are not configured. The most relevant passages are shown in the citations below. "
                    + "Set OPENAI_API_KEY to enable generated answers.";
        }

        Map<String, Object> body = chatBody(question, context);
        String answer = request(chatUrl(), body)
                .path("choices").path(0).path("message").path("content").asText("").trim();
        if (answer.isBlank()) throw new AiProviderException("AI provider returned an empty answer");
        return answer;
    }

    public void streamAnswer(
            String question,
            String context,
            Consumer<String> onDelta,
            BooleanSupplier cancelled
    ) {
        if (!configured()) {
            streamLocalFallback(onDelta, cancelled);
            return;
        }

        Map<String, Object> body = chatBody(question, context);
        body.put("stream", true);
        RestClient.RequestBodySpec request = authenticatedRequest(chatUrl())
                .accept(MediaType.TEXT_EVENT_STREAM);
        try {
            request.body(body).exchange((httpRequest, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new AiProviderException(
                            "AI provider rejected the streaming request (HTTP "
                                    + response.getStatusCode().value() + ")");
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!cancelled.getAsBoolean() && (line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) continue;
                        JsonNode chunk = objectMapper.readTree(data);
                        String delta = chunk.path("choices").path(0).path("delta")
                                .path("content").asText("");
                        if (!delta.isEmpty()) onDelta.accept(delta);
                    }
                }
                return null;
            });
        } catch (StreamCancelledException ex) {
            throw ex;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(
                    "AI provider rejected the streaming request (HTTP "
                            + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new AiProviderException("AI provider stream is unavailable or timed out", ex);
        } catch (Exception ex) {
            throw new AiProviderException("AI provider returned an invalid stream", ex);
        }
    }

    private JsonNode request(String url, Map<String, Object> body) {
        RestClient.RequestBodySpec request = authenticatedRequest(url);
        try {
            JsonNode response = request.body(body).retrieve().body(JsonNode.class);
            if (response == null) throw new AiProviderException("AI provider returned an empty response");
            return response;
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(
                    "AI provider rejected the request (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new AiProviderException("AI provider is unavailable or timed out", ex);
        }
    }

    private RestClient.RequestBodySpec authenticatedRequest(String url) {
        RestClient.RequestBodySpec request = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        if (isAzure()) request.header("api-key", config.apiKey());
        else request.header("Authorization", "Bearer " + config.apiKey());
        return request;
    }

    private Map<String, Object> chatBody(String question, String context) {
        String system = """
                You are an enterprise knowledge assistant answering from retrieved document passages.
                The application calls you only when retrieval has found useful context, so always provide
                the best answer supported by that context. Do not refuse to answer or say there is not enough
                information when any supplied passage addresses the question. If coverage is partial, answer
                the supported portion and briefly identify the specific detail that remains unclear.

                For definition questions, begin with a direct one- or two-sentence definition synthesized from
                the passages, then add relevant details. For summary questions, synthesize the main ideas across
                all relevant passages instead of merely listing or repeating them.

                Cite every factual sentence or paragraph inline using one or more labels such as [Source 1].
                Keep source labels exactly as provided so they map to document and page metadata. Use only the
                supplied context, distinguish facts from reasonable synthesis, and never invent details.
                """;
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.chatModel());
        body.put("temperature", 0.1);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "Context:\n" + context + "\n\nQuestion: " + question)
        ));
        return body;
    }

    private void streamLocalFallback(Consumer<String> onDelta, BooleanSupplier cancelled) {
        String fallback = "AI credentials are not configured. The most relevant passages are shown "
                + "in the citations below. Set OPENAI_API_KEY to enable generated answers.";
        for (String part : fallback.split("(?<=\\s)")) {
            if (cancelled.getAsBoolean()) throw new StreamCancelledException();
            onDelta.accept(part);
        }
    }

    private boolean configured() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    private boolean isAzure() {
        return "azure".equalsIgnoreCase(config.provider());
    }

    private String embeddingUrl() {
        if (isAzure()) {
            return stripSlash(config.baseUrl()) + "/openai/deployments/" + config.embeddingModel()
                    + "/embeddings?api-version=" + config.azureApiVersion();
        }
        return stripSlash(config.baseUrl()) + "/embeddings";
    }

    private String chatUrl() {
        if (isAzure()) {
            return stripSlash(config.baseUrl()) + "/openai/deployments/" + config.chatModel()
                    + "/chat/completions?api-version=" + config.azureApiVersion();
        }
        return stripSlash(config.baseUrl()) + "/chat/completions";
    }

    private String stripSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new AiProviderException("AI provider base URL is not configured");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private List<Double> deterministicEmbedding(String text) {
        double[] vector = new double[config.embeddingDimensions()];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String[] terms = text.toLowerCase(Locale.ROOT).split("\\W+");
            for (String term : terms) {
                if (term.isBlank()) continue;
                byte[] hash = digest.digest(term.getBytes(StandardCharsets.UTF_8));
                int index = ((hash[0] & 0xff) << 8 | (hash[1] & 0xff)) % vector.length;
                vector[index] += (hash[2] & 1) == 0 ? 1.0 : -1.0;
            }
        } catch (Exception ex) {
            throw new AiProviderException("Could not generate fallback embedding", ex);
        }
        double norm = Math.sqrt(Arrays.stream(vector).map(v -> v * v).sum());
        List<Double> result = new ArrayList<>(vector.length);
        for (double value : vector) result.add(norm == 0 ? 0 : value / norm);
        return result;
    }
}
