package com.payments.payment.api;

import com.payments.payment.dto.PagedResponse;
import com.payments.payment.dto.PaymentRequest;
import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.service.PaymentFacade;
import com.payments.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Thin controller: validation by annotations, logic in services. */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentFacade paymentFacade;
    private final PaymentQueryService paymentQueryService;

    @Operation(summary = "Create a payment",
            description = "Returns 202 with status RECEIVED. Authorization and settlement continue asynchronously over Kafka.")
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Parameter(description = "UUID chosen by the client; resending the same key returns the same payment")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        var result = paymentFacade.create(idempotencyKey, request);
        var response = ResponseEntity.accepted()
                .location(URI.create("/api/v1/payments/" + result.body().paymentId()));
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(result.body());
    }

    @Operation(summary = "Get a payment's current status (Redis cache first, then PostgreSQL)")
    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return paymentQueryService.get(paymentId);
    }

    @Operation(summary = "List a merchant's payments, newest first")
    @GetMapping
    public PagedResponse<PaymentResponse> list(@RequestParam String merchantId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return paymentQueryService.listByMerchant(merchantId, page, size);
    }
}
