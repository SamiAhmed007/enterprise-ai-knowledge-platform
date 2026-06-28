package com.enterpriseai.knowledge.security;

import com.enterpriseai.knowledge.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class AiRateLimitFilter extends OncePerRequestFilter {
    private static final Pattern CHAT_ENDPOINT = Pattern.compile(
            "^/api/workspaces/[^/]+/chats/(ask|stream)$");
    private static final Pattern DOCUMENT_UPLOAD_ENDPOINT = Pattern.compile(
            "^/api/workspaces/[^/]+/documents/?$");

    private final RateLimitService rateLimits;
    private final ObjectMapper objectMapper;

    public AiRateLimitFilter(RateLimitService rateLimits, ObjectMapper objectMapper) {
        this.rateLimits = rateLimits;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitService.Bucket bucket = classify(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (bucket == null || authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitService.Decision decision = rateLimits.consume(authentication.getName(), bucket);
        if (decision.allowed()) {
            response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
            response.setHeader("X-RateLimit-Remaining",
                    Long.toString(Math.max(0, decision.limit() - decision.count())));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = decision.retryAfterSeconds();
        String message = bucket == RateLimitService.Bucket.CHAT
                ? "Chat request limit exceeded. Try again in " + retryAfter + " seconds."
                : "Document upload limit exceeded. Try again in " + retryAfter + " seconds.";
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", "0");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(429, "Too Many Requests", message, request.getRequestURI()));
    }

    private RateLimitService.Bucket classify(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (CHAT_ENDPOINT.matcher(path).matches()) {
            return RateLimitService.Bucket.CHAT;
        }
        if (DOCUMENT_UPLOAD_ENDPOINT.matcher(path).matches()) {
            return RateLimitService.Bucket.DOCUMENT_UPLOAD;
        }
        return null;
    }
}
