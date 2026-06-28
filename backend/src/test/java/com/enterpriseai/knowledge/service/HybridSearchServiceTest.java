package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HybridSearchServiceTest {
    @Test
    void combinesNormalizedKeywordAndVectorScoresBeforeRanking() {
        VectorStore store = mock(VectorStore.class);
        HybridSearchService service = new HybridSearchService(
                store, new AppProperties.Retrieval(3, 0.6, 0.4, 0.15));
        UUID semanticOnly = UUID.randomUUID();
        UUID hybrid = UUID.randomUUID();
        UUID keywordOnly = UUID.randomUUID();

        List<VectorStore.SearchCandidate> semantic = List.of(
                candidate(semanticOnly, "semantic.txt", 0, 0.9),
                candidate(hybrid, "hybrid.pdf", 2, 0.7)
        );
        List<VectorStore.SearchCandidate> keyword = List.of(
                candidate(keywordOnly, "keyword.txt", 1, 0.8),
                candidate(hybrid, "hybrid.pdf", 2, 0.4)
        );

        List<HybridSearchService.SearchResult> ranked = service.rank(semantic, keyword);

        assertThat(ranked).extracting(HybridSearchService.SearchResult::documentName)
                .containsExactly("hybrid.pdf", "semantic.txt", "keyword.txt");
        assertThat(ranked.get(0).pageNumber()).isEqualTo(2);
        assertThat(ranked.get(0).vectorScore()).isEqualTo(0.7);
        assertThat(ranked.get(0).keywordScore()).isEqualTo(0.5);
        assertThat(ranked.get(0).score()).isEqualTo(0.62);
    }

    @Test
    void rejectsRetrievalWeightsThatCannotProduceARanking() {
        assertThatThrownBy(() -> new HybridSearchService(
                mock(VectorStore.class), new AppProperties.Retrieval(5, 0, 0, 0.15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weights");
    }

    @Test
    void removesCandidatesBelowTheMinimumHybridScore() {
        HybridSearchService service = new HybridSearchService(
                mock(VectorStore.class), new AppProperties.Retrieval(5, 0.7, 0.3, 0.15));
        List<VectorStore.SearchCandidate> semantic = List.of(
                candidate(UUID.randomUUID(), "unrelated.pdf", 1, 0.1));

        assertThat(service.rank(semantic, List.of())).isEmpty();
    }

    private VectorStore.SearchCandidate candidate(
            UUID chunkId,
            String documentName,
            Integer pageNumber,
            double relevance
    ) {
        return new VectorStore.SearchCandidate(
                chunkId,
                UUID.randomUUID(),
                documentName,
                1,
                pageNumber,
                "Relevant content",
                relevance
        );
    }
}
