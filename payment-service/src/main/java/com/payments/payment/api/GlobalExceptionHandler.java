package com.payments.payment.api;

import com.payments.common.web.ApiError;
import com.payments.common.web.BaseApiExceptionHandler;
import com.payments.payment.exception.InvalidIdempotencyKeyException;
import com.payments.payment.idempotency.IdempotencyInProgressException;
import com.payments.payment.idempotency.IdempotencyKeyReusedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseApiExceptionHandler {

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handleInvalidKey(InvalidIdempotencyKeyException ex) {
        return badRequest("INVALID_IDEMPOTENCY_KEY", ex.getMessage(), "Idempotency-Key");
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ApiError> handleInProgress(IdempotencyInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("IDEMPOTENCY_IN_PROGRESS", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiError> handleReused(IdempotencyKeyReusedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of("IDEMPOTENCY_KEY_REUSED", ex.getMessage(), "Idempotency-Key"));
    }

    /** Redis or the database is unreachable: tell the client to retry later (with the same Idempotency-Key). */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiError> handleUnavailable(DataAccessResourceFailureException ex) {
        log.error("Dependency unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("SERVICE_UNAVAILABLE", "A dependency is temporarily unavailable. Retry with the same Idempotency-Key."));
    }
}
