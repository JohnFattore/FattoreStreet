package com.fattorestreet.sec_api.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every error as {@code {"detail": "..."}}, the same shape Django REST Framework returns,
 * so the frontend reads errors from both backends with one parser ({@code getErrorMessages}).
 *
 * <p>Spring MVC's own exceptions (missing or mistyped parameters, unreadable bodies, unknown
 * routes, {@link org.springframework.web.server.ResponseStatusException}) keep the status Spring
 * assigns them; only the body changes. Constraint violations from the {@code @Validated}
 * controllers become 400s, and anything else is a 500 with a generic message so internals never
 * reach the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Invalid request." : detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers).body(Map.of("detail", detailFor(ex, statusCode)));
    }

    private static String detailFor(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MethodArgumentNotValidException manv) {
            return joinMessages(manv.getAllErrors());
        }
        if (ex instanceof HandlerMethodValidationException hmve) {
            return joinMessages(hmve.getAllErrors());
        }
        if (ex instanceof ErrorResponse er && er.getBody().getDetail() != null) {
            return er.getBody().getDetail();
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : "Request failed.";
    }

    private static String joinMessages(List<? extends MessageSourceResolvable> errors) {
        String joined = errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? "Invalid request." : joined;
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(Map.of("detail", detail));
    }
}
