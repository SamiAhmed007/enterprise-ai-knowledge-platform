package com.enterpriseai.knowledge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestTracingFilter extends OncePerRequestFilter {
    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_MDC_KEY = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long started = System.nanoTime();
        String correlationId = correlationId(request.getHeader(CORRELATION_HEADER));
        response.setHeader(CORRELATION_HEADER, correlationId);

        try (MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable(CORRELATION_MDC_KEY, correlationId);
             MDC.MDCCloseable ignoredMethod = MDC.putCloseable("httpMethod", request.getMethod());
             MDC.MDCCloseable ignoredEndpoint = MDC.putCloseable("endpoint", safeEndpoint(request))) {
            addIdentity();
            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove("userId");
            }
        } finally {
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            try (MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable(CORRELATION_MDC_KEY, correlationId);
                 MDC.MDCCloseable ignoredMethod = MDC.putCloseable("httpMethod", request.getMethod());
                 MDC.MDCCloseable ignoredEndpoint = MDC.putCloseable("endpoint", safeEndpoint(request));
                 MDC.MDCCloseable ignoredStatus = MDC.putCloseable("statusCode",
                         Integer.toString(response.getStatus()));
                 MDC.MDCCloseable ignoredLatency = MDC.putCloseable("latencyMs",
                         Long.toString(latencyMs))) {
                addIdentity();
                try {
                    if (response.getStatus() >= 500) log.error("http.request.completed");
                    else if (response.getStatus() >= 400) log.warn("http.request.completed");
                    else log.info("http.request.completed");
                } finally {
                    MDC.remove("userId");
                }
            }
        }
    }

    private void addIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlatformPrincipal principal) {
            MDC.put("userId", principal.userId().toString());
        }
    }

    private String correlationId(String candidate) {
        return candidate != null && SAFE_CORRELATION_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    private String safeEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.length() <= 500 ? path : path.substring(0, 500);
    }
}
