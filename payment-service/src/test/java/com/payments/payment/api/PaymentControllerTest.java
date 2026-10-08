package com.payments.payment.api;

import com.payments.payment.dto.PagedResponse;
import com.payments.payment.dto.PaymentRequest;
import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.exception.InvalidIdempotencyKeyException;
import com.payments.payment.exception.PaymentNotFoundException;
import com.payments.payment.idempotency.IdempotencyInProgressException;
import com.payments.payment.idempotency.IdempotencyKeyReusedException;
import com.payments.payment.idempotency.IdempotentResult;
import com.payments.payment.service.PaymentFacade;
import com.payments.payment.service.PaymentQueryService;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String KEY = "3f0c8a52-7d1e-4e7b-9a43-1b2c3d4e5f60";
    private static final String VALID_BODY = """
            {"merchantId":"MER-1001","cardNumber":"4111111111111111","expiryMonth":12,"expiryYear":2028,
             "amount":2499.00,"currency":"INR"}
            """;

    @Autowired
    MockMvc mvc;
    @MockBean
    PaymentFacade facade;
    @MockBean
    PaymentQueryService queryService;

    private final PaymentResponse response = PaymentResponse.from(TestData.payment());

    @Test
    void createReturns202WithReceivedStatus() throws Exception {
        when(facade.create(eq(KEY), any(PaymentRequest.class))).thenReturn(new IdempotentResult<>(response, false));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/payments/" + response.paymentId()))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.paymentId").value(response.paymentId().toString()))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.maskedCard").value("**** 1111"));
    }

    @Test
    void replayIsFlaggedWithHeader() throws Exception {
        when(facade.create(eq(KEY), any(PaymentRequest.class))).thenReturn(new IdempotentResult<>(response, true));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.timestamp").exists());
        verify(facade, never()).create(anyString(), any());
    }

    @Test
    void invalidBodyIs400WithField() throws Exception {
        String badCard = VALID_BODY.replace("4111111111111111", "4111111111111112");

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(badCard))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("cardNumber"));
    }

    @Test
    void negativeAmountAndUnknownCurrencyAre400() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("2499.00", "-5").replace("INR", "GBP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void idempotencyErrorsMapToTheRightStatus() throws Exception {
        doThrow(new InvalidIdempotencyKeyException()).when(facade).create(anyString(), any());
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));

        doThrow(new IdempotencyInProgressException(KEY)).when(facade).create(anyString(), any());
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"));

        doThrow(new IdempotencyKeyReusedException(KEY)).when(facade).create(anyString(), any());
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        doThrow(new RedisConnectionFailureException("down")).when(facade).create(anyString(), any());
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void getReturnsPayment() throws Exception {
        when(queryService.get(response.paymentId())).thenReturn(response);

        mvc.perform(get("/api/v1/payments/{id}", response.paymentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void unknownPaymentIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryService.get(id)).thenThrow(new PaymentNotFoundException(id));

        mvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void badPaymentIdIs400() throws Exception {
        mvc.perform(get("/api/v1/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void listRequiresMerchantId() throws Exception {
        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    void listReturnsPage() throws Exception {
        when(queryService.listByMerchant("MER-1001", 0, 20))
                .thenReturn(new PagedResponse<>(List.of(response), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/payments").param("merchantId", "MER-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentId").value(response.paymentId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        when(queryService.get(any())).thenThrow(new QueryTimeoutException("boom"));
        // QueryTimeoutException is a TransientDataAccessException, not a resource failure -> generic 500

        mvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
