package com.payments.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Evicts a payment from the Redis status cache. Eviction runs AFTER the database commit: evicting before commit
 * would let a concurrent reader re-cache the old status between the eviction and the commit.
 */
@Component
@RequiredArgsConstructor
public class PaymentStatusCache {

    public static final String CACHE_NAME = "paymentStatus";

    private final CacheManager cacheManager;

    public void evictAfterCommit(UUID paymentId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(paymentId);
                }
            });
        } else {
            evict(paymentId);
        }
    }

    public void evict(UUID paymentId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(paymentId.toString());
        }
    }
}
