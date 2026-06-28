package com.enterpriseai.knowledge.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class VectorStore {
    private final JdbcTemplate jdbc;

    public VectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(
            UUID documentId,
            int index,
            Integer pageNumber,
            String content,
            List<Double> embedding
    ) {
        jdbc.update("""
                INSERT INTO document_chunks
                    (id, document_id, chunk_index, page_number, content, embedding, token_estimate)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?)
                """,
                UUID.randomUUID(), documentId, index, pageNumber, content, vectorLiteral(embedding),
                Math.max(1, content.length() / 4));
    }

    public void deleteByDocument(UUID documentId) {
        jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    public List<SearchCandidate> semanticSearch(UUID workspaceId, List<Double> embedding, int limit) {
        return jdbc.query("""
                SELECT dc.id AS chunk_id, dc.document_id, d.original_name,
                       dc.chunk_index, dc.page_number, dc.content,
                       1 - (dc.embedding <=> ?::vector) AS relevance
                FROM document_chunks dc
                JOIN documents d ON d.id = dc.document_id
                WHERE d.workspace_id = ? AND d.status = 'READY'
                ORDER BY dc.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new SearchCandidate(
                        rs.getObject("chunk_id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("original_name"),
                        rs.getInt("chunk_index"),
                        rs.getObject("page_number", Integer.class),
                        rs.getString("content"),
                        rs.getDouble("relevance")
                ),
                vectorLiteral(embedding), workspaceId, vectorLiteral(embedding), limit);
    }

    public List<SearchCandidate> keywordSearch(UUID workspaceId, String query, int limit) {
        return jdbc.query("""
                WITH search_query AS (
                    SELECT to_tsquery(
                        'english',
                        array_to_string(
                            tsvector_to_array(to_tsvector('english', ?)),
                            ' | '
                        )
                    ) AS value
                )
                SELECT dc.id AS chunk_id, dc.document_id, d.original_name,
                       dc.chunk_index, dc.page_number, dc.content,
                       ts_rank_cd(dc.search_vector, search_query.value, 32) AS relevance
                FROM document_chunks dc
                JOIN documents d ON d.id = dc.document_id
                CROSS JOIN search_query
                WHERE d.workspace_id = ?
                  AND d.status = 'READY'
                  AND dc.search_vector @@ search_query.value
                ORDER BY relevance DESC, dc.chunk_index
                LIMIT ?
                """,
                (rs, rowNum) -> new SearchCandidate(
                        rs.getObject("chunk_id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("original_name"),
                        rs.getInt("chunk_index"),
                        rs.getObject("page_number", Integer.class),
                        rs.getString("content"),
                        rs.getDouble("relevance")
                ),
                query, workspaceId, limit);
    }

    private String vectorLiteral(List<Double> vector) {
        return vector.toString();
    }

    public record SearchCandidate(
            UUID chunkId,
            UUID documentId,
            String documentName,
            int chunkIndex,
            Integer pageNumber,
            String content,
            double relevance
    ) {}
}
