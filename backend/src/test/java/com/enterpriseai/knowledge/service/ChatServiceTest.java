package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.ChatSession;
import com.enterpriseai.knowledge.domain.MessageRole;
import com.enterpriseai.knowledge.domain.Workspace;
import com.enterpriseai.knowledge.dto.ChatDtos.AskRequest;
import com.enterpriseai.knowledge.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {
    private ChatSessionRepository sessions;
    private AiClient ai;
    private HybridSearchService retrieval;
    private WorkspaceService workspaces;
    private ChatService service;
    private AppUser user;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        sessions = mock(ChatSessionRepository.class);
        ai = mock(AiClient.class);
        retrieval = mock(HybridSearchService.class);
        workspaces = mock(WorkspaceService.class);
        service = new ChatService(sessions, ai, retrieval, workspaces, new ObjectMapper());
        user = AppUser.builder().id(UUID.randomUUID()).name("User").email("user@example.com").build();
        workspace = Workspace.builder()
                .id(UUID.randomUUID()).owner(user).name("Knowledge").build();
        when(workspaces.requireAccessible(user, workspace.getId())).thenReturn(workspace);
        when(ai.embedding(anyString())).thenReturn(List.of(0.1, 0.2));
        when(sessions.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(UUID.randomUUID());
            return session;
        });
    }

    @Test
    void askPersistsQuestionAnswerAndCitationMetadata() {
        UUID documentId = UUID.randomUUID();
        when(retrieval.search(eq(workspace.getId()), eq("What is retention?"), anyList()))
                .thenReturn(List.of(new HybridSearchService.SearchResult(
                        documentId, "policy.pdf", 2, 4, "Retention is seven years.",
                        0.92, 0.9, 1.0)));
        when(ai.answer(eq("What is retention?"), anyString()))
                .thenReturn("Retention is seven years. [Source 1]");

        var response = service.ask(
                user, workspace.getId(), new AskRequest(null, " What is retention? "));

        ArgumentCaptor<ChatSession> saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getMessages()).hasSize(2);
        assertThat(saved.getValue().getMessages().get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(saved.getValue().getMessages().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(saved.getValue().getMessages().get(1).getCitationsJson())
                .contains(documentId.toString()).contains("policy.pdf");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.answer()).contains("[Source 1]");
    }

    @Test
    void askWithoutRelevantContextReturnsGroundedFallbackWithoutCallingChatProvider() {
        when(retrieval.search(eq(workspace.getId()), anyString(), anyList())).thenReturn(List.of());

        var response = service.ask(
                user, workspace.getId(), new AskRequest(null, "Unknown topic"));

        assertThat(response.answer()).contains("couldn't find sufficiently relevant information");
        verify(ai, never()).answer(anyString(), anyString());
        verify(sessions).save(any(ChatSession.class));
    }

    @Test
    void answersDefinitionQuestionWheneverRetrievedChunksAreAvailable() {
        UUID documentId = UUID.randomUUID();
        when(retrieval.search(eq(workspace.getId()), eq("What is data retention?"), anyList()))
                .thenReturn(List.of(new HybridSearchService.SearchResult(
                        documentId, "governance.pdf", 3, 7,
                        "Data retention is the controlled preservation of records for a defined period.",
                        0.88, 0.84, 0.97)));
        when(ai.answer(eq("What is data retention?"), anyString()))
                .thenReturn("Data retention is the controlled preservation of records for a defined period.");

        var response = service.ask(
                user, workspace.getId(), new AskRequest(null, "What is data retention?"));

        assertThat(response.answer())
                .contains("controlled preservation")
                .endsWith("[Source 1]");
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.documentId()).isEqualTo(documentId);
            assertThat(citation.pageNumber()).isEqualTo(7);
        });
        verify(ai).answer(eq("What is data retention?"),
                org.mockito.ArgumentMatchers.contains("Data retention is the controlled preservation"));
    }

    @Test
    void answersSummaryQuestionUsingAllRetrievedChunks() {
        when(retrieval.search(eq(workspace.getId()), eq("Summarize the security policy"), anyList()))
                .thenReturn(List.of(
                        new HybridSearchService.SearchResult(
                                UUID.randomUUID(), "security.pdf", 0, 1,
                                "Access requires multi-factor authentication.",
                                0.91, 0.9, 0.94),
                        new HybridSearchService.SearchResult(
                                UUID.randomUUID(), "security.pdf", 1, 2,
                                "Security events are reviewed every day.",
                                0.86, 0.83, 0.93)));
        when(ai.answer(eq("Summarize the security policy"), anyString()))
                .thenReturn("The policy requires MFA [Source 1] and daily event review [Source 2].");

        var response = service.ask(
                user, workspace.getId(), new AskRequest(null, "Summarize the security policy"));

        assertThat(response.answer()).contains("[Source 1]").contains("[Source 2]");
        assertThat(response.citations()).hasSize(2);
        verify(ai).answer(eq("Summarize the security policy"),
                org.mockito.ArgumentMatchers.argThat(context ->
                        context.contains("multi-factor authentication")
                                && context.contains("reviewed every day")));
    }

    @Test
    void cannotAppendToAnotherUsersSession() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findWithMessagesByIdAndWorkspaceIdAndUserId(
                sessionId, workspace.getId(), user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(
                user, workspace.getId(), new AskRequest(sessionId, "Question")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chat session not found");
        verify(sessions, never()).save(any());
    }

    @Test
    void listUsesBothWorkspaceAndUserBoundaries() {
        ChatSession session = ChatSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .workspace(workspace)
                .title("Policy")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(sessions.findAllByWorkspaceIdAndUserIdOrderByUpdatedAtDesc(
                workspace.getId(), user.getId())).thenReturn(List.of(session));

        var result = service.list(user, workspace.getId());

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(session.getId());
            assertThat(summary.workspaceId()).isEqualTo(workspace.getId());
        });
    }
}
