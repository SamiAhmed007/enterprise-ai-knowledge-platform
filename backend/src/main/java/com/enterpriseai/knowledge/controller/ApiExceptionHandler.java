package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.dto.ApiError;
import com.enterpriseai.knowledge.service.AiProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        return error(status, HttpStatus.valueOf(status).getReasonPhrase(), ex.getReason(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(HttpServletRequest request) {
        return error(401, "Unauthorized", "Invalid email or password", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ApiError body = new ApiError(
                400,
                "Bad Request",
                "Validation failed",
                request.getRequestURI(),
                org.slf4j.MDC.get("correlationId"),
                Instant.now(),
                fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class
    })
    ResponseEntity<ApiError> malformedRequest(Exception ex, HttpServletRequest request) {
        return error(400, "Bad Request", "The request body or parameters are invalid", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(HttpServletRequest request) {
        return error(413, "Payload Too Large", "The maximum upload size is 25 MB", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Database constraint violation for {} {}", request.getMethod(), request.getRequestURI());
        return error(409, "Conflict", "The request conflicts with existing data", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate(HttpServletRequest request) {
        return error(409, "Conflict",
                "This resource was updated concurrently; refresh and try again", request);
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<ApiError> aiProvider(AiProviderException ex, HttpServletRequest request) {
        log.warn("AI provider request failed ({})", ex.getClass().getSimpleName());
        return error(502, "Bad Gateway", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(500, "Internal Server Error", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> error(
            int status,
            String error,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(ApiError.of(
                status, error, message == null ? "Request failed" : message, request.getRequestURI()));
    }
}
