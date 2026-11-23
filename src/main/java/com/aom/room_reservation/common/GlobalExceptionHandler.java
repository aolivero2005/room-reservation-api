package com.aom.room_reservation.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns every error into an RFC 9457 Problem Details response (application/problem+json).
 * <p>
 * Extending {@link ResponseEntityExceptionHandler} gives correct statuses for all standard
 * Spring MVC errors (405, 415, missing parameter...). We only override the ones whose default
 * message is not clear enough for an API client, and add our domain exceptions.
 * <p>
 * Validation errors carry an extra {@code errors} array: [{"field": "...", "message": "..."}].
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record FieldErrorDetail(String field, String message) {
    }

    // ---------- Domain exceptions ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // ---------- 400: validation ----------

    /** @Valid @RequestBody failed: one entry per invalid field (or per class-level rule). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldErrorDetail> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldErrorDetail(
                        error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();

        return validationProblem(ex, errors, headers, status, request);
    }

    /** Constraints on @RequestParam / @PathVariable (e.g. @Min on a query parameter) failed. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldErrorDetail> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();

        return validationProblem(ex, errors, headers, status, request);
    }

    // ---------- 400: unreadable input ----------

    /**
     * Malformed JSON or a value of the wrong type (e.g. "start": "tomorrow").
     * Points at the offending field when Jackson knows it, without leaking parser internals.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String detail = findJacksonFieldPath(ex)
                .map(field -> "Field '" + field + "' has an invalid value or format")
                .orElse("Request body is missing or is not valid JSON");
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Malformed request", detail);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Walks the cause chain looking for the Jackson exception that knows which field failed.
     * Not just the root cause: for a bad date the root is a DateTimeParseException with no field info.
     */
    private static Optional<String> findJacksonFieldPath(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                return Optional.of(jackson.getPath().stream()
                        .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                        .collect(Collectors.joining(".")));
            }
        }
        return Optional.empty();
    }

    /** Path variable or query parameter of the wrong type (e.g. /api/rooms/abc, ?date=yesterday). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "a different type";
        String detail = "Parameter '" + ex.getPropertyName() + "' has an invalid value '" + ex.getValue()
                + "' (expected " + expected + ")";
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid parameter", detail);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Pagination sorted by an unknown property (e.g. ?sort=foo); otherwise this would be a 500. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleInvalidSort(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort property",
                "Cannot sort by '" + ex.getPropertyName() + "'");
    }

    // ---------- 500: anything unexpected ----------

    /** Logs the real cause and returns a generic message so internals are never exposed. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred");
    }

    // ---------- helpers ----------

    private ResponseEntity<Object> validationProblem(Exception ex, List<FieldErrorDetail> errors,
                                                     HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request contains invalid fields");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
