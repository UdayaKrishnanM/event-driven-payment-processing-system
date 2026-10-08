package com.payments.payment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** Uses the (merchant_id, created_at) index. */
    Page<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);
}
