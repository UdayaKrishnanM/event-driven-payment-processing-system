package com.payments.payment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentStatusCacheTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final PaymentStatusCache statusCache = new PaymentStatusCache(cacheManager);
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsImmediatelyOutsideATransaction() {
        when(cacheManager.getCache(PaymentStatusCache.CACHE_NAME)).thenReturn(cache);

        statusCache.evictAfterCommit(id);

        verify(cache).evict(id.toString());
    }

    @Test
    void insideATransactionEvictsOnlyAfterCommit() {
        when(cacheManager.getCache(PaymentStatusCache.CACHE_NAME)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        statusCache.evictAfterCommit(id);
        verify(cache, never()).evict(id.toString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache).evict(id.toString());
    }

    @Test
    void missingCacheIsIgnored() {
        when(cacheManager.getCache(PaymentStatusCache.CACHE_NAME)).thenReturn(null);

        statusCache.evict(id);
    }
}
