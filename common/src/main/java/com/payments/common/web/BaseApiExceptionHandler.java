package com.payments.common.web;

import com.payments.common.util.CardMasker;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;

/**
 * Shared error mapping. Each service declares a {@code @RestControllerAdvice} that extends this class
 * and adds its own service-specific handlers.
 */
@Slf4j
public abstract class BaseApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        var fieldError = ex.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparing(FieldError::getField));
        if (fieldError.isPresent()) {
            FieldError fe = fieldError.get();
            return badRequest("VALIDATION_FAILED", fe.getDefaultMessage(), fe.getField());
        }
        String message = ex.getBindingResult().getGlobalErrors().stream()
                .map(ObjectError::getDefaultMessage).findFirst().orElse("Request is invalid");
        return badRequest("VALIDATION_FAILED", message, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream().findFirst()
                .map(e -> e.getDefaultMessage()).orElse("Request is invalid");
        return badRequest("VALIDATION_FAILED", message, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        var violation = ex.getConstraintViolations().stream().findFirst();
        String field = violation.map(v -> v.getPropertyPath().toString()).orElse(null);
        String message = violation.map(v -> v.getMessage()).orElse("Request is invalid");
        return badRequest("VALIDATION_FAILED", message, field);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        String code = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())
                ? "MISSING_IDEMPOTENCY_KEY" : "MISSING_HEADER";
        return badRequest(code, "Required header '" + ex.getHeaderName() + "' is missing", ex.getHeaderName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing", ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("INVALID_PARAMETER", "Parameter '" + ex.getName() + "' has an invalid value", ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("MALFORMED_REQUEST", "Request body is missing or is not valid JSON", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED", "HTTP method " + ex.getMethod() + " is not supported here"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "Use Content-Type: application/json"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "No endpoint " + ex.getResourcePath()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", CardMasker.maskPans(String.valueOf(ex.getMessage())), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Something went wrong. Please try again later."));
    }

    protected ResponseEntity<ApiError> badRequest(String code, String message, String field) {
        return ResponseEntity.badRequest().body(ApiError.of(code, message, field));
    }
}
