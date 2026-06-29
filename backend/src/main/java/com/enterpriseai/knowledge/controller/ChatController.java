package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.dto.ChatDtos.*;
import com.enterpriseai.knowledge.service.ChatService;
import com.enterpriseai.knowledge.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/chats")
public class ChatController {
    private final ChatService chats;
    private final CurrentUserService currentUser;

    public ChatController(ChatService chats, CurrentUserService currentUser) {
        this.chats = chats;
        this.currentUser = currentUser;
    }

    @PostMapping("/ask")
    public AskResponse ask(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AskRequest request
    ) {
        return chats.ask(currentUser.require(authentication), workspaceId, request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AskRequest request
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chats.stream(currentUser.require(authentication), workspaceId, request, emitter);
        return emitter;
    }

    @GetMapping
    public List<SessionSummary> list(
            Authentication authentication,
            @PathVariable UUID workspaceId
    ) {
        return chats.list(currentUser.require(authentication), workspaceId);
    }

    @GetMapping("/{id}")
    public SessionDetail get(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id
    ) {
        return chats.get(currentUser.require(authentication), workspaceId, id);
    }

    @PatchMapping("/{id}")
    public SessionSummary rename(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @Valid @RequestBody RenameSessionRequest request
    ) {
        return chats.rename(currentUser.require(authentication), workspaceId, id, request.title());
    }

    @PostMapping("/{id}/regenerate")
    public AskResponse regenerate(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id
    ) {
        return chats.regenerate(currentUser.require(authentication), workspaceId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication authentication,
            @PathVariable UUID workspaceId,
            @PathVariable UUID id
    ) {
        chats.delete(currentUser.require(authentication), workspaceId, id);
    }
}
