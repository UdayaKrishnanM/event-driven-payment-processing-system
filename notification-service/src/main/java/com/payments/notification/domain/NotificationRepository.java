package com.payments.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    Page<Notification> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId, Pageable pageable);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
