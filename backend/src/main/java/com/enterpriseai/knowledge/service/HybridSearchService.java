package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HybridSearchService {
    private final VectorStore vectorStore;
    private final AppProperties.Retrieval config;

    @Autowired
    public HybridSearchService(VectorStore vectorStore, AppProperties properties) {
        this(vectorStore, properties.retrieval());
    }

    HybridSearchService(VectorStore vectorStore, AppProperties.Retrieval config) {
        if (config.topK() < 1) throw new IllegalArgumentException("RAG topK must be at least 1");
        if (config.vectorWeight() < 0 || config.keywordWeight() < 0
                || config.vectorWeight() + config.keywordWeight() <= 0) {
            throw new IllegalArgumentException("RAG retrieval weights must be non-negative and not both zero");
        }
        if (config.minimumScore() < 0 || config.minimumScore() > 1) {
            throw new IllegalArgumentException("RAG minimum score must be between 0 and 1");
        }
        this.vectorStore = vectorStore;
        this.config = config;
    }

    public List<SearchResult> search(UUID workspaceId, String query, List<Double> embedding) {
        int candidateLimit = Math.max(config.topK() * 4, 20);
        List<VectorStore.SearchCandidate> semantic =
                vectorStore.semanticSearch(workspaceId, embedding, candidateLimit);
        List<VectorStore.SearchCandidate> keyword =
                vectorStore.keywordSearch(workspaceId, query, candidateLimit);
        return rank(semantic, keyword);
    }

    List<SearchResult> rank(
            List<VectorStore.SearchCandidate> semantic,
            List<VectorStore.SearchCandidate> keyword
    ) {
        Map<UUID, MutableScore> merged = new LinkedHashMap<>();
        semantic.forEach(candidate -> merged
                .computeIfAbsent(candidate.chunkId(), ignored -> new MutableScore(candidate))
                .vectorScore = clamp(candidate.relevance()));
        keyword.forEach(candidate -> merged
                .computeIfAbsent(candidate.chunkId(), ignored -> new MutableScore(candidate))
                .keywordScore = Math.max(0, candidate.relevance()));

        double maxKeyword = merged.values().stream()
                .mapToDouble(score -> score.keywordScore)
                .max()
                .orElse(0);
        double weightTotal = config.vectorWeight() + config.keywordWeight();

        List<SearchResult> results = new ArrayList<>();
        for (MutableScore score : merged.values()) {
            double normalizedKeyword = maxKeyword == 0 ? 0 : score.keywordScore / maxKeyword;
            double hybridScore = (
                    config.vectorWeight() * score.vectorScore
                            + config.keywordWeight() * normalizedKeyword
            ) / weightTotal;
            VectorStore.SearchCandidate chunk = score.chunk;
            results.add(new SearchResult(
                    chunk.documentId(),
                    chunk.documentName(),
                    chunk.chunkIndex(),
                    chunk.pageNumber(),
                    chunk.content(),
                    hybridScore,
                    score.vectorScore,
                    normalizedKeyword
            ));
        }

        return results.stream()
                .filter(result -> result.score() >= config.minimumScore())
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed()
                        .thenComparing(Comparator.comparingDouble(SearchResult::keywordScore).reversed())
                        .thenComparing(Comparator.comparingDouble(SearchResult::vectorScore).reversed())
                        .thenComparing(SearchResult::documentName)
                        .thenComparingInt(SearchResult::chunkIndex))
                .limit(config.topK())
                .toList();
    }

    private double clamp(double score) {
        return Math.max(0, Math.min(1, score));
    }

    private static final class MutableScore {
        private final VectorStore.SearchCandidate chunk;
        private double vectorScore;
        private double keywordScore;

        private MutableScore(VectorStore.SearchCandidate chunk) {
            this.chunk = chunk;
        }
    }

    public record SearchResult(
            UUID documentId,
            String documentName,
            int chunkIndex,
            Integer pageNumber,
            String content,
            double score,
            double vectorScore,
            double keywordScore
    ) {}
}
