package com.payments.common.web;

import java.time.Instant;

/** Consistent error body returned by every service: code, message, field, timestamp. */
public record ApiError(String code, String message, String field, Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field, Instant.now());
    }
}
