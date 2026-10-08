package com.payments.notification;

import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import com.payments.notification.domain.FailedEvent;
import com.payments.notification.domain.FailedEventRepository;
import com.payments.notification.domain.NotificationRepository;
import com.payments.notification.support.ContainersSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** notification-service against real Kafka and PostgreSQL, including the DLT monitor and replay. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationFlowIT extends ContainersSupport {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    FailedEventRepository failedEvents;
    @Autowired
    MockMvc mvc;

    private PaymentEvent event(EventType type, UUID paymentId, String merchant) {
        return PaymentEvent.create(type, paymentId, merchant, new BigDecimal("99.00"), "INR", "**** 1111",
                type == EventType.PAYMENT_DECLINED ? null : "A1B2C3",
                type == EventType.PAYMENT_DECLINED ? "LIMIT_EXCEEDED" : null, Instant.now());
    }

    @Test
    void everyOutcomeBecomesANotificationAndDuplicatesAreIgnored() throws Exception {
        String merchant = "MER-N-" + UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentEvent authorized = event(EventType.PAYMENT_AUTHORIZED, paymentId, merchant);
        PaymentEvent settled = authorized.next(EventType.PAYMENT_SETTLED, Instant.now());
        PaymentEvent declined = event(EventType.PAYMENT_DECLINED, UUID.randomUUID(), merchant);

        kafkaTemplate.send(Topics.PAYMENT_AUTHORIZED, authorized.key(), authorized).get();
        kafkaTemplate.send(Topics.PAYMENT_SETTLED, settled.key(), settled).get();
        kafkaTemplate.send(Topics.PAYMENT_SETTLED, settled.key(), settled).get(); // redelivery
        kafkaTemplate.send(Topics.PAYMENT_DECLINED, declined.key(), declined).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notifications.findByMerchantIdOrderByCreatedAtDesc(merchant, PageRequest.of(0, 10))
                        .getTotalElements()).isEqualTo(3));

        mvc.perform(get("/api/v1/notifications").param("merchantId", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void poisonMessageGoesToDltIsStoredAndCanBeReplayedOnceFixed() throws Exception {
        String key = UUID.randomUUID().toString();
        // Not JSON at all: the deserializer fails -> not retryable -> payment.declined.DLT -> failed_events
        kafkaTemplate.send(Topics.PAYMENT_DECLINED, key, "this is not json").get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(failedEvents.findAll()).anySatisfy(f -> {
                    assertThat(f.getKey()).isEqualTo(key);
                    assertThat(f.getTopic()).isEqualTo("payment.declined.DLT");
                    assertThat(f.getOriginalTopic()).isEqualTo("payment.declined");
                }));

        // Replay a stored, valid event (as an operator would after fixing the data)
        String merchant = "MER-REPLAY-" + UUID.randomUUID();
        PaymentEvent fixed = event(EventType.PAYMENT_DECLINED, UUID.randomUUID(), merchant);
        String json = """
                {"eventId":"%s","eventType":"PAYMENT_DECLINED","paymentId":"%s","merchantId":"%s","amount":5.00,
                 "currency":"INR","maskedCard":"**** 1111","declineReason":"LIMIT_EXCEEDED",
                 "occurredAt":"2026-10-20T10:15:30Z","schemaVersion":1}
                """.formatted(fixed.eventId(), fixed.paymentId(), merchant);
        FailedEvent stored = failedEvents.save(FailedEvent.of("payment.declined.DLT", "payment.declined",
                "notification-service", fixed.key(), json, "manual", 0, Long.MAX_VALUE - 1, Instant.now()));

        mvc.perform(post("/api/v1/admin/failed-events/{id}/replay", stored.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayCount").value(1));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notifications.findByMerchantIdOrderByCreatedAtDesc(merchant, PageRequest.of(0, 10))
                        .getTotalElements()).isEqualTo(1));
    }
}
