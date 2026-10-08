package com.payments.ledger.dto;

import com.payments.ledger.domain.Direction;
import com.payments.ledger.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(UUID id, String account, Direction direction, BigDecimal amount, String currency,
                                  Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(e.getId(), e.getAccount(), e.getDirection(), e.getAmount(),
                e.getCurrency(), e.getCreatedAt());
    }
}
