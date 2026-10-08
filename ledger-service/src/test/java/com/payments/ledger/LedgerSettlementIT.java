package com.payments.ledger;

import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import com.payments.ledger.chaos.ChaosSettings;
import com.payments.ledger.domain.LedgerEntryRepository;
import com.payments.ledger.domain.LedgerQueries;
import com.payments.ledger.support.ContainersSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** ledger-service against real Kafka and PostgreSQL. */
@SpringBootTest
class LedgerSettlementIT extends ContainersSupport {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    LedgerEntryRepository entryRepository;
    @Autowired
    LedgerQueries ledgerQueries;
    @Autowired
    ChaosSettings chaosSettings;

    @AfterEach
    void chaosOff() {
        chaosSettings.setFailMerchantId(null);
    }

    private PaymentEvent authorized(String merchantId, String amount) {
        return PaymentEvent.create(EventType.PAYMENT_AUTHORIZED, UUID.randomUUID(), merchantId, new BigDecimal(amount),
                "INR", "**** 1111", "A1B2C3", null, Instant.now());
    }

    private void publish(PaymentEvent e) throws Exception {
        kafkaTemplate.send(Topics.PAYMENT_AUTHORIZED, e.key(), e).get();
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        var consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /** Polls until a record with this key shows up (or the timeout passes). */
    private ConsumerRecord<String, String> awaitRecord(String topic, String key, Duration timeout) {
        try (var consumer = consumer(topic)) {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    if (key.equals(r.key())) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    /** Exception class, cause class and message headers the DLT recoverer adds, joined for easy assertions. */
    private static String exceptionInfo(ConsumerRecord<String, String> record) {
        StringBuilder sb = new StringBuilder();
        for (String name : List.of(KafkaHeaders.DLT_EXCEPTION_FQCN, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN,
                KafkaHeaders.DLT_EXCEPTION_MESSAGE)) {
            var h = record.headers().lastHeader(name);
            if (h != null) {
                sb.append(new String(h.value(), StandardCharsets.UTF_8)).append(' ');
            }
        }
        return sb.toString();
    }

    @Test
    void authorizedPaymentIsPostedAsDebitPlusCreditAndSettledIsPublished() throws Exception {
        PaymentEvent event = authorized("MER-LEDGER-1", "2499.00");
        publish(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(entryRepository.countByPaymentId(event.paymentId())).isEqualTo(2));

        ConsumerRecord<String, String> settled = awaitRecord(Topics.PAYMENT_SETTLED, event.key(), Duration.ofSeconds(10));
        assertThat(settled).isNotNull();
        assertThat(settled.value()).contains("PAYMENT_SETTLED").contains(event.paymentId().toString());
    }

    @Test
    void sameEventDeliveredTwiceStillGivesExactlyTwoRows() throws Exception {
        PaymentEvent event = authorized("MER-LEDGER-2", "10.00");
        publish(event);
        publish(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(entryRepository.countByPaymentId(event.paymentId())).isEqualTo(2));
        Thread.sleep(1500); // give a (wrong) second posting time to show up
        assertThat(entryRepository.countByPaymentId(event.paymentId())).isEqualTo(2);
    }

    @Test
    void totalDebitsEqualTotalCreditsAfterFiftyPayments() throws Exception {
        List<PaymentEvent> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            PaymentEvent e = authorized("MER-LEDGER-" + (i % 5), (i + 1) + ".25");
            events.add(e);
            publish(e);
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(events.stream().allMatch(e -> entryRepository.countByPaymentId(e.paymentId()) == 2)).isTrue());

        var trial = ledgerQueries.trialBalance();
        assertThat(trial.balanced()).isTrue();
        assertThat(trial.totalDebits()).isEqualByComparingTo(trial.totalCredits());
    }

    @Test
    void failingConsumerRetriesThreeTimesThenSendsToDlt() throws Exception {
        chaosSettings.setFailMerchantId("MER-CHAOS");
        PaymentEvent event = authorized("MER-CHAOS", "77.00");
        long start = System.currentTimeMillis();
        publish(event);

        ConsumerRecord<String, String> dead =
                awaitRecord(Topics.dltOf(Topics.PAYMENT_AUTHORIZED), event.key(), Duration.ofSeconds(30));

        assertThat(dead).isNotNull();
        // 3 retries with 1s + 2s + 4s backoff = at least ~7 seconds before the DLT
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(6_500);
        assertThat(exceptionInfo(dead)).containsAnyOf("SimulatedLedgerFailureException", "Simulated ledger");
        assertThat(dead.partition()).isBetween(0, 2);
        assertThat(entryRepository.countByPaymentId(event.paymentId())).isZero();
    }

    @Test
    void badDataGoesToDltWithoutRetrying() throws Exception {
        PaymentEvent bad = new PaymentEvent(UUID.randomUUID(), EventType.PAYMENT_AUTHORIZED, UUID.randomUUID(),
                "MER-BAD", BigDecimal.ZERO, "INR", "**** 1111", "A", null, Instant.now(), 1);
        kafkaTemplate.send(Topics.PAYMENT_AUTHORIZED, bad.key(), bad).get();

        ConsumerRecord<String, String> dead =
                awaitRecord(Topics.dltOf(Topics.PAYMENT_AUTHORIZED), bad.key(), Duration.ofSeconds(20));

        assertThat(dead).isNotNull();
        // ValidationException is registered as not-retryable, so it skipped the 1s/2s/4s backoff
        assertThat(exceptionInfo(dead)).contains("ValidationException");
    }
}
