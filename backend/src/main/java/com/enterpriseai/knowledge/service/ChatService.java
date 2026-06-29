package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.*;
import com.enterpriseai.knowledge.dto.ChatDtos.*;
import com.enterpriseai.knowledge.repository.ChatSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatSessionRepository sessions;
    private final AiClient aiClient;
    private final HybridSearchService retrieval;
    private final WorkspaceService workspaces;
    private final ObjectMapper objectMapper;

    public ChatService(
            ChatSessionRepository sessions,
            AiClient aiClient,
            HybridSearchService retrieval,
            WorkspaceService workspaces,
            ObjectMapper objectMapper
    ) {
        this.sessions = sessions;
        this.aiClient = aiClient;
        this.retrieval = retrieval;
        this.workspaces = workspaces;
        this.objectMapper = objectMapper;
    }

    public AskResponse ask(AppUser user, UUID workspaceId, AskRequest request) {
        PreparedChat prepared = prepare(user, workspaceId, request);
        String answer = prepared.context().isBlank()
                ? noContextAnswer()
                : aiClient.answer(prepared.question(), prepared.context());
        answer = ensureCitation(answer, prepared.citations());
        ChatSession saved = saveCompleted(prepared, answer);
        return new AskResponse(saved.getId(), answer, prepared.citations());
    }

    @Async
    public void stream(AppUser user, UUID workspaceId, AskRequest request, SseEmitter emitter) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(error -> cancelled.set(true));

        try {
            PreparedChat prepared = prepare(user, workspaceId, request);
            send(emitter, "sources", new StreamSources(prepared.citations()));

            StringBuilder answer = new StringBuilder();
            if (prepared.context().isBlank()) {
                emitText(emitter, noContextAnswer(), answer, cancelled);
            } else {
                aiClient.streamAnswer(
                        prepared.question(),
                        prepared.context(),
                        delta -> emitDelta(emitter, delta, answer, cancelled),
                        cancelled::get);
            }

            if (cancelled.get()) return;
            String finalAnswer = answer.toString().trim();
            if (finalAnswer.isBlank()) {
                send(emitter, "error", streamError("The AI provider returned an empty response"));
                emitter.complete();
                return;
            }
            String citedAnswer = ensureCitation(finalAnswer, prepared.citations());
            if (!citedAnswer.equals(finalAnswer)) {
                emitDelta(emitter, citedAnswer.substring(finalAnswer.length()), answer, cancelled);
                finalAnswer = citedAnswer;
            }

            ChatSession saved = saveCompleted(prepared, finalAnswer);
            send(emitter, "done", new StreamComplete(saved.getId(), prepared.citations()));
            emitter.complete();
        } catch (StreamCancelledException ignored) {
            emitter.complete();
        } catch (Exception ex) {
            log.error("Streaming chat failed ({})", ex.getClass().getSimpleName());
            if (!cancelled.get()) {
                try {
                    String message = ex instanceof AiProviderException
                            ? ex.getMessage()
                            : "The streamed response could not be completed";
                    send(emitter, "error", streamError(message));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ex);
                }
            }
        }
    }

    private StreamError streamError(String message) {
        return new StreamError(message, MDC.get("correlationId"));
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> list(AppUser user, UUID workspaceId) {
        workspaces.requireAccessible(user, workspaceId);
        return sessions.findAllByWorkspaceIdAndUserIdOrderByUpdatedAtDesc(
                        workspaceId, user.getId()).stream()
                .map(session -> new SessionSummary(
                        session.getId(), workspaceId, session.getTitle(),
                        session.getCreatedAt(), session.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDetail get(AppUser user, UUID workspaceId, UUID id) {
        workspaces.requireAccessible(user, workspaceId);
        ChatSession session = sessions.findWithMessagesByIdAndWorkspaceIdAndUserId(
                        id, workspaceId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        List<MessageResponse> messages = session.getMessages().stream()
                .map(message -> new MessageResponse(
                        message.getId(), message.getRole(), message.getContent(),
                        readCitations(message.getCitationsJson()), message.getCreatedAt()))
                .toList();
        return new SessionDetail(
                session.getId(), workspaceId, session.getTitle(),
                session.getCreatedAt(), session.getUpdatedAt(), messages);
    }

    @Transactional
    public void delete(AppUser user, UUID workspaceId, UUID id) {
        workspaces.requireAccessible(user, workspaceId);
        ChatSession session = sessions.findWithMessagesByIdAndWorkspaceIdAndUserId(
                        id, workspaceId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        sessions.delete(session);
    }

    @Transactional
    public SessionSummary rename(AppUser user, UUID workspaceId, UUID id, String requestedTitle) {
        workspaces.requireAccessible(user, workspaceId);
        ChatSession session = requireSession(user, workspaceId, id);
        String title = requestedTitle.strip();
        if (title.isBlank() || title.length() > 250) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation title is invalid");
        }
        session.setTitle(title);
        session.setUpdatedAt(Instant.now());
        ChatSession saved = sessions.save(session);
        return new SessionSummary(
                saved.getId(), workspaceId, saved.getTitle(),
                saved.getCreatedAt(), saved.getUpdatedAt());
    }

    public AskResponse regenerate(AppUser user, UUID workspaceId, UUID id) {
        workspaces.requireAccessible(user, workspaceId);
        ChatSession session = requireSession(user, workspaceId, id);
        int lastUserIndex = -1;
        for (int i = session.getMessages().size() - 1; i >= 0; i--) {
            if (session.getMessages().get(i).getRole() == MessageRole.USER) {
                lastUserIndex = i;
                break;
            }
        }
        if (lastUserIndex < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This conversation has no question to regenerate");
        }
        String question = session.getMessages().get(lastUserIndex).getContent();
        while (session.getMessages().size() > lastUserIndex + 1) {
            session.getMessages().remove(session.getMessages().size() - 1);
        }
        PreparedChat prepared = prepareGrounding(session, question);
        String answer = prepared.context().isBlank()
                ? noContextAnswer()
                : aiClient.answer(prepared.question(), prepared.context());
        answer = ensureCitation(answer, prepared.citations());
        ChatSession saved = saveCompleted(prepared, answer);
        return new AskResponse(saved.getId(), answer, prepared.citations());
    }

    private ChatSession newSession(AppUser user, Workspace workspace, String question) {
        String title = question.strip();
        if (title.length() > 80) title = title.substring(0, 80) + "…";
        return ChatSession.builder().user(user).workspace(workspace).title(title).build();
    }

    private PreparedChat prepare(AppUser user, UUID workspaceId, AskRequest request) {
        Workspace workspace = workspaces.requireAccessible(user, workspaceId);
        String question = request.question().strip();
        ChatSession session = request.sessionId() == null
                ? newSession(user, workspace, question)
                : sessions.findWithMessagesByIdAndWorkspaceIdAndUserId(
                            request.sessionId(), workspaceId, user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        addMessage(session, MessageRole.USER, question, null);
        session.setUpdatedAt(Instant.now());

        return prepareGrounding(session, question);
    }

    private PreparedChat prepareGrounding(ChatSession session, String question) {
        List<HybridSearchService.SearchResult> matches =
                retrieval.search(session.getWorkspace().getId(), question, aiClient.embedding(question));
        StringBuilder context = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            HybridSearchService.SearchResult match = matches.get(i);
            context.append("[Source ").append(i + 1)
                    .append(" | Document: ").append(match.documentName());
            if (match.pageNumber() != null) {
                context.append(" | Page: ").append(match.pageNumber());
            }
            context.append(" | Chunk: ").append(match.chunkIndex()).append("]\n")
                    .append(match.content()).append("\n\n");
            citations.add(new Citation(
                    match.documentId(),
                    match.documentName(),
                    match.chunkIndex(),
                    match.pageNumber(),
                    excerpt(match.content()),
                    match.score(),
                    match.vectorScore(),
                    match.keywordScore(),
                    "HYBRID"));
        }
        return new PreparedChat(session, question, context.toString(), citations);
    }

    private ChatSession requireSession(AppUser user, UUID workspaceId, UUID id) {
        return sessions.findWithMessagesByIdAndWorkspaceIdAndUserId(
                        id, workspaceId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    private ChatSession saveCompleted(PreparedChat prepared, String answer) {
        if (answer == null || answer.isBlank()) {
            throw new AiProviderException("The AI provider returned an empty response");
        }
        addMessage(prepared.session(), MessageRole.ASSISTANT, answer, writeCitations(prepared.citations()));
        prepared.session().setUpdatedAt(Instant.now());
        return sessions.save(prepared.session());
    }

    private String noContextAnswer() {
        return "I couldn't find sufficiently relevant information in the READY documents "
                + "for this workspace. Try a more specific question or upload another source.";
    }

    private String ensureCitation(String answer, List<Citation> citations) {
        if (answer == null || answer.isBlank() || citations.isEmpty()
                || answer.matches("(?s).*\\[Source\\s+\\d+].*")) {
            return answer;
        }
        return answer.stripTrailing() + " [Source 1]";
    }

    private void emitText(
            SseEmitter emitter,
            String text,
            StringBuilder answer,
            AtomicBoolean cancelled
    ) {
        for (String part : text.split("(?<=\\s)")) {
            emitDelta(emitter, part, answer, cancelled);
        }
    }

    private void emitDelta(
            SseEmitter emitter,
            String delta,
            StringBuilder answer,
            AtomicBoolean cancelled
    ) {
        if (cancelled.get()) throw new StreamCancelledException();
        answer.append(delta);
        send(emitter, "delta", new StreamDelta(delta));
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ex) {
            throw new StreamCancelledException();
        }
    }

    private void addMessage(ChatSession session, MessageRole role, String content, String citations) {
        session.getMessages().add(ChatMessage.builder()
                .session(session)
                .role(role)
                .content(content)
                .citationsJson(citations)
                .build());
    }

    private String excerpt(String text) {
        return text.length() <= 240 ? text : text.substring(0, 240) + "…";
    }

    private String writeCitations(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize citations", ex);
        }
    }

    private List<Citation> readCitations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private record PreparedChat(
            ChatSession session,
            String question,
            String context,
            List<Citation> citations
    ) {}
}
