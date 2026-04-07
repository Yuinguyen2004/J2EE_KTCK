package com.billiard.shared.web;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String UNEXPECTED_ERROR_MESSAGE =
            "An unexpected server error occurred. Please try again.";

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String detailMessage = StringUtils.hasText(ex.getReason())
                ? ex.getReason()
                : defaultDetail(status);

        if (status.is5xxServerError()) {
            log.error("Request failed with {}: {}", status.value(), detailMessage, ex);
        } else {
            log.warn("Request rejected with {}: {}", status.value(), detailMessage);
        }

        return createProblemDetail(status, detailMessage, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<Map<String, String>> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toViolation)
                .toList();
        String detailMessage = violations.isEmpty()
                ? "The request contains invalid data."
                : violations.stream()
                        .map(violation -> violation.get("field") + ": " + violation.get("message"))
                        .distinct()
                        .reduce((left, right) -> left + "; " + right)
                        .orElse("The request contains invalid data.");

        ProblemDetail detail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                detailMessage,
                "Validation failed"
        );
        detail.setProperty("violations", violations);
        return detail;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail detail = createProblemDetail(
                HttpStatus.CONFLICT,
                "The resource was modified by another request. Please retry.",
                "Concurrent modification conflict"
        );
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                UNEXPECTED_ERROR_MESSAGE,
                "Unexpected server error"
        );
    }

    private ProblemDetail createProblemDetail(
            HttpStatusCode status,
            String detailMessage,
            String title
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, detailMessage);
        detail.setTitle(StringUtils.hasText(title) ? title : defaultDetail(status));
        detail.setProperty("message", detailMessage);
        return detail;
    }

    private String defaultDetail(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return httpStatus.getReasonPhrase();
        }
        return "Request failed";
    }

    private Map<String, String> toViolation(FieldError error) {
        String message = StringUtils.hasText(error.getDefaultMessage())
                ? error.getDefaultMessage()
                : "Invalid value";
        return Map.of(
                "field", error.getField(),
                "message", message
        );
    }
}
