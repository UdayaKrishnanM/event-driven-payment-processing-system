package com.payments.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.common.domain.PaymentStatus;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.support.ContainersSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment-service against real Kafka, PostgreSQL and Redis.
 * The ledger is not running in this JVM, so the test plays the ledger's part by publishing payment.settled itself.
 * (The full 3-service flow is checked with docker compose + the Postman collection.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIT extends ContainersSupport {

    @Autowired
    MockMvc mvc;
    @Autowired
    PaymentRepository repository;
    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    ObjectMapper objectMapper;

    private static final Random RANDOM = new Random();

    /** Random Luhn-valid Visa number so the velocity rule never interferes between tests. */
    static String randomCard() {
        StringBuilder body = new StringBuilder("4");
        for (int i = 0; i < 14; i++) {
            body.append(RANDOM.nextInt(10));
        }
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int d = body.charAt(body.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return body.append((10 - sum % 10) % 10).toString();
    }

    private String body(String card, String amount) {
        return """
                {"merchantId":"MER-IT","cardNumber":"%s","expiryMonth":12,"expiryYear":2030,"amount":%s,"currency":"INR"}
                """.formatted(card, amount);
    }

    private MvcResult postPayment(String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/payments").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private UUID paymentIdOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("paymentId").asText());
    }

    @Test
    void paymentGoesFromReceivedToAuthorizedToSettled() throws Exception {
        MvcResult created = postPayment(UUID.randomUUID().toString(), body(randomCard(), "2499.00"));
        assertThat(created.getResponse().getStatus()).isEqualTo(202);
        UUID id = paymentIdOf(created);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED));

        Payment p = repository.findById(id).orElseThrow();
        assertThat(p.getAuthCode()).matches("[A-Z0-9]{6}");

        // Play the ledger: publish payment.settled.
        PaymentEvent settled = PaymentEvent.create(EventType.PAYMENT_SETTLED, id, p.getMerchantId(), p.getAmount(),
                p.getCurrency(), p.getMaskedCard(), p.getAuthCode(), null, Instant.now());
        kafkaTemplate.send(Topics.PAYMENT_SETTLED, id.toString(), settled).get();
        // ... twice, like a Kafka redelivery: must be a no-op the second time
        kafkaTemplate.send(Topics.PAYMENT_SETTLED, id.toString(), settled).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SETTLED));

        // Status read goes through the Redis cache, which was evicted on each change.
        mvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.maskedCard").value(p.getMaskedCard()));
    }

    @Test
    void sameIdempotencyKeyTwiceCreatesOnePaymentAndSameResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body(randomCard(), "100.00");

        MvcResult first = postPayment(key, body);
        MvcResult second = postPayment(key, body);

        assertThat(second.getResponse().getStatus()).isEqualTo(202);
        assertThat(paymentIdOf(second)).isEqualTo(paymentIdOf(first));
        assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(repository.findByIdempotencyKey(key)).isPresent();
        assertThat(repository.findAll().stream().filter(p -> p.getIdempotencyKey().equals(key)).count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        String card = randomCard();
        postPayment(key, body(card, "100.00"));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(card, "200.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void cardEnding0002IsDeclinedForInsufficientFunds() throws Exception {
        UUID id = paymentIdOf(postPayment(UUID.randomUUID().toString(), body("4000000000000002", "10.00")));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Payment p = repository.findById(id).orElseThrow();
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.DECLINED);
            assertThat(p.getDeclineReason()).isEqualTo("INSUFFICIENT_FUNDS");
        });
    }

    @Test
    void amountOverLimitIsDeclined() throws Exception {
        UUID id = paymentIdOf(postPayment(UUID.randomUUID().toString(), body(randomCard(), "60000.00")));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getDeclineReason()).isEqualTo("LIMIT_EXCEEDED"));
    }

    @Test
    void sixthPaymentOnSameCardWithinAMinuteFailsVelocityCheck() throws Exception {
        String card = randomCard();
        UUID last = null;
        for (int i = 0; i < 6; i++) {
            last = paymentIdOf(postPayment(UUID.randomUUID().toString(), body(card, "1.0" + i)));
        }
        UUID sixth = last;

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findAll().stream()
                        .filter(p -> p.getStatus() == PaymentStatus.DECLINED)
                        .anyMatch(p -> "VELOCITY_CHECK_FAILED".equals(p.getDeclineReason()))).isTrue());
        assertThat(sixth).isNotNull();
    }

    @Test
    void invalidRequestIs400() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body("1234", "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("cardNumber"))
                .andExpect(header().doesNotExist("Location"));
    }
}
