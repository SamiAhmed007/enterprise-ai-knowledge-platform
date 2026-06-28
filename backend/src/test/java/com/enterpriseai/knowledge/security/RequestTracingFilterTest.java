package com.enterpriseai.knowledge.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTracingFilterTest {
    private final RequestTracingFilter filter = new RequestTracingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesSafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/workspaces");
        request.addHeader(RequestTracingFilter.CORRELATION_HEADER, "support-case-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(RequestTracingFilter.CORRELATION_MDC_KEY))
                    .isEqualTo("support-case-123");
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204);
        });

        assertThat(response.getHeader(RequestTracingFilter.CORRELATION_HEADER))
                .isEqualTo("support-case-123");
        assertThat(MDC.get(RequestTracingFilter.CORRELATION_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/workspaces");
        request.addHeader(RequestTracingFilter.CORRELATION_HEADER, "bad id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestTracingFilter.CORRELATION_HEADER))
                .matches("[0-9a-f-]{36}")
                .isNotEqualTo("bad id with spaces");
    }
}
