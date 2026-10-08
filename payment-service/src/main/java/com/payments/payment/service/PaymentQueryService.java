package com.payments.payment.service;

import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.dto.PagedResponse;
import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reads. Single-payment status is served from the Redis cache first, then PostgreSQL. */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository paymentRepository;

    @Cacheable(cacheNames = PaymentStatusCache.CACHE_NAME, key = "#paymentId.toString()")
    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> listByMerchant(String merchantId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PagedResponse.from(
                paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(safePage, safeSize)),
                PaymentResponse::from);
    }
}
