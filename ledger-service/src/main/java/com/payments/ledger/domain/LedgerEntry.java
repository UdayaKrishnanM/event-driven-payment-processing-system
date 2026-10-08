package com.payments.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One side of a double-entry posting. Immutable once written. */
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 100)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static LedgerEntry of(UUID paymentId, String account, Direction direction, BigDecimal amount,
                                 String currency, Instant now) {
        LedgerEntry e = new LedgerEntry();
        e.id = UUID.randomUUID();
        e.paymentId = paymentId;
        e.account = account;
        e.direction = direction;
        e.amount = amount;
        e.currency = currency;
        e.createdAt = now;
        return e;
    }
}
