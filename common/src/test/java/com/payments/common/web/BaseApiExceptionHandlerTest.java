package com.payments.common.web;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseApiExceptionHandlerTest {

    private final BaseApiExceptionHandler handler = new BaseApiExceptionHandler() { };

    record Body(@NotNull(message = "name is required") String name) { }

    private static void assertError(ResponseEntity<ApiError> r, HttpStatus status, String code) {
        assertThat(r.getStatusCode()).isEqualTo(status);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }

    @Test
    void fieldValidationErrorsReportTheField() {
        var binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "amount", "amount must be greater than 0"));
        var ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        var r = handler.handleValidation(ex);

        assertError(r, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(r.getBody().field()).isEqualTo("amount");
        assertThat(r.getBody().message()).isEqualTo("amount must be greater than 0");
    }

    @Test
    void globalValidationErrorsHaveNoField() {
        var binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new ObjectError("req", "card is expired"));
        var ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        var r = handler.handleValidation(ex);

        assertError(r, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(r.getBody().field()).isNull();
        assertThat(r.getBody().message()).isEqualTo("card is expired");
    }

    @Test
    void methodValidation() {
        var ex = mock(HandlerMethodValidationException.class);
        doReturn(List.of(new DefaultMessageSourceResolvable(null, null, "size too big"))).when(ex).getAllErrors();

        var r = handler.handleMethodValidation(ex);

        assertError(r, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(r.getBody().message()).isEqualTo("size too big");
    }

    @Test
    void constraintViolations() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Body(null));
            var r = handler.handleConstraintViolation(new ConstraintViolationException(violations));

            assertError(r, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
            assertThat(r.getBody().field()).isEqualTo("name");
            assertThat(r.getBody().message()).isEqualTo("name is required");
        }
    }

    @Test
    void missingIdempotencyKeyHasItsOwnCode() {
        var ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("Idempotency-Key");
        assertError(handler.handleMissingHeader(ex), HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY");

        var other = mock(MissingRequestHeaderException.class);
        when(other.getHeaderName()).thenReturn("X-Other");
        assertError(handler.handleMissingHeader(other), HttpStatus.BAD_REQUEST, "MISSING_HEADER");
    }

    @Test
    void otherClientErrors() {
        assertError(handler.handleMissingParam(new MissingServletRequestParameterException("merchantId", "String")),
                HttpStatus.BAD_REQUEST, "MISSING_PARAMETER");

        var mismatch = mock(MethodArgumentTypeMismatchException.class);
        when(mismatch.getName()).thenReturn("paymentId");
        assertError(handler.handleTypeMismatch(mismatch), HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");

        assertError(handler.handleUnreadable(mock(HttpMessageNotReadableException.class)),
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
        assertError(handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("DELETE")),
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
        assertError(handler.handleMediaType(mock(HttpMediaTypeNotSupportedException.class)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");

        var noResource = mock(NoResourceFoundException.class);
        when(noResource.getResourcePath()).thenReturn("/nope");
        assertError(handler.handleNoResource(noResource), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void notFoundUsesTheExceptionsCode() {
        var r = handler.handleNotFound(new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment x not found"));

        assertError(r, HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");
        assertThat(r.getBody().message()).isEqualTo("Payment x not found");
    }

    @Test
    void unexpectedErrorsHideInternals() {
        var r = handler.handleUnexpected(new RuntimeException("NPE at card 4111111111111111"));

        assertError(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message()).doesNotContain("4111");
    }

    @Test
    void apiErrorFactories() {
        assertThat(ApiError.of("X", "m").field()).isNull();
        assertThat(ApiError.of("X", "m", "f").field()).isEqualTo("f");
    }
}
