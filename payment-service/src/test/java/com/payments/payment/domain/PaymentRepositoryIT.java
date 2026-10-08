package com.payments.payment.domain;

import com.payments.payment.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JPA slice against a real PostgreSQL (Flyway migrations applied). Each repository call commits on its own. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PaymentRepository repository;

    private Payment newPayment(String key) {
        return Payment.receive(UUID.randomUUID(), key, "MER-REPO", "**** 1111", "fp", 12, 2028,
                new BigDecimal("10.00"), "INR", Instant.now());
    }

    @Test
    void uniqueConstraintBlocksDuplicateIdempotencyKey() {
        String key = UUID.randomUUID().toString();
        repository.saveAndFlush(newPayment(key));

        assertThatThrownBy(() -> repository.saveAndFlush(newPayment(key)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void versionBlocksAStaleUpdate() {
        Payment saved = repository.saveAndFlush(newPayment(UUID.randomUUID().toString()));
        Payment copyA = repository.findById(saved.getId()).orElseThrow();
        Payment copyB = repository.findById(saved.getId()).orElseThrow();

        copyA.authorize("A1B2C3", Instant.now());
        repository.saveAndFlush(copyA);

        copyB.decline("LIMIT_EXCEEDED", Instant.now());
        assertThatThrownBy(() -> repository.saveAndFlush(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void listsMerchantPaymentsNewestFirst() {
        String merchant = "MER-" + UUID.randomUUID();
        Payment older = Payment.receive(UUID.randomUUID(), UUID.randomUUID().toString(), merchant, "**** 1111", "fp",
                12, 2028, BigDecimal.ONE, "INR", Instant.parse("2026-01-01T00:00:00Z"));
        Payment newer = Payment.receive(UUID.randomUUID(), UUID.randomUUID().toString(), merchant, "**** 1111", "fp",
                12, 2028, BigDecimal.TEN, "INR", Instant.parse("2026-02-01T00:00:00Z"));
        repository.saveAndFlush(older);
        repository.saveAndFlush(newer);

        var page = repository.findByMerchantIdOrderByCreatedAtDesc(merchant, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Payment::getId).containsExactly(newer.getId(), older.getId());
        assertThat(TestData.payment()).isNotNull();
    }
}
