package com.enterpriseai.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimitFilterTest {
    private RateLimitService rateLimits;
    private AiRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        rateLimits = mock(RateLimitService.class);
        filter = new AiRateLimitFilter(rateLimits, new ObjectMapper().findAndRegisterModules());
        chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsChatRequestWhenUserIsWithinLimit() throws Exception {
        when(rateLimits.consume("user@example.com", RateLimitService.Bucket.CHAT))
                .thenReturn(new RateLimitService.Decision(true, 20, 4, 0));
        MockHttpServletRequest request = post("/api/workspaces/workspace-id/chats/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("16");
    }

    @Test
    void rejectsDocumentUploadWithClear429Response() throws Exception {
        when(rateLimits.consume("user@example.com", RateLimitService.Bucket.DOCUMENT_UPLOAD))
                .thenReturn(new RateLimitService.Decision(false, 30, 31, 1800));
        MockHttpServletRequest request = post("/api/workspaces/workspace-id/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1800");
        assertThat(response.getContentAsString())
                .contains("Document upload limit exceeded")
                .contains("Try again in 1800 seconds");
    }

    @Test
    void doesNotCountDocumentRetryAsANewUpload() throws Exception {
        MockHttpServletRequest request = post(
                "/api/workspaces/workspace-id/documents/document-id/retry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimits, never()).consume(
                "user@example.com", RateLimitService.Bucket.DOCUMENT_UPLOAD);
    }

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }
}
