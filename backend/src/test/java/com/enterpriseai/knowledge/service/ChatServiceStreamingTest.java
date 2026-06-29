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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceStreamingTest {
    private ChatSessionRepository sessions;
    private AiClient aiClient;
    private HybridSearchService retrieval;
    private ChatService service;
    private AppUser user;
    private Workspace workspace;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        sessions = mock(ChatSessionRepository.class);
        aiClient = mock(AiClient.class);
        retrieval = mock(HybridSearchService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        service = new ChatService(sessions, aiClient, retrieval, workspaces, new ObjectMapper());
        user = AppUser.builder().id(UUID.randomUUID()).name("Test").email("test@example.com").build();
        workspaceId = UUID.randomUUID();
        workspace = Workspace.builder().id(workspaceId).name("Test Workspace").owner(user).build();

        when(aiClient.embedding(anyString())).thenReturn(List.of(0.1, 0.2));
        when(workspaces.requireAccessible(user, workspaceId)).thenReturn(workspace);
        when(retrieval.search(eq(workspaceId), anyString(), anyList())).thenReturn(List.of(
                new HybridSearchService.SearchResult(
                        UUID.randomUUID(), "policy.txt", 0, null,
                        "Policy context", 0.9, 0.85, 1.0)));
        when(sessions.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(UUID.randomUUID());
            return session;
        });
    }

    @Test
    void savesOnlyTheCompleteStreamedAnswer() {
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("Hello ");
            consumer.accept("world");
            return null;
        }).when(aiClient).streamAnswer(anyString(), anyString(), any(), any());
        CapturingEmitter emitter = new CapturingEmitter(Integer.MAX_VALUE);

        service.stream(user, workspaceId, new AskRequest(null, "What is the policy?"), emitter);

        ArgumentCaptor<ChatSession> saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getMessages()).hasSize(2);
        assertThat(saved.getValue().getMessages().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(saved.getValue().getMessages().get(1).getContent())
                .isEqualTo("Hello world [Source 1]");
        assertThat(emitter.events).isEqualTo(5);
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void doesNotPersistAnEmptyProviderResponse() {
        doNothing().when(aiClient).streamAnswer(anyString(), anyString(), any(), any());
        CapturingEmitter emitter = new CapturingEmitter(Integer.MAX_VALUE);

        service.stream(user, workspaceId, new AskRequest(null, "What is the policy?"), emitter);

        verify(sessions, never()).save(any());
        assertThat(emitter.events).isEqualTo(2);
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void doesNotPersistAStreamCancelledByTheClient() {
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("partial");
            return null;
        }).when(aiClient).streamAnswer(anyString(), anyString(), any(), any());
        CapturingEmitter emitter = new CapturingEmitter(2);

        service.stream(user, workspaceId, new AskRequest(null, "What is the policy?"), emitter);

        verify(sessions, never()).save(any());
        assertThat(emitter.completed).isTrue();
    }

    private static final class CapturingEmitter extends SseEmitter {
        private final int failAtEvent;
        private int events;
        private boolean completed;

        private CapturingEmitter(int failAtEvent) {
            this.failAtEvent = failAtEvent;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            events++;
            if (events >= failAtEvent) throw new IOException("client disconnected");
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }
    }
}
