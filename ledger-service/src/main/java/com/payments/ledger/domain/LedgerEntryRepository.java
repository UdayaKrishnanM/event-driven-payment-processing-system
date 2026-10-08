package com.payments.ledger.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByPaymentIdOrderByDirectionAsc(UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);

    long countByPaymentId(UUID paymentId);
}
