package com.payments.ledger.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Failure injection for the retry/DLT demo. When set, every authorized payment from this merchant makes the
 * ledger consumer throw a (retryable) exception, so you can watch 3 retries (1s, 2s, 4s) and the DLT.
 * Off by default; can be changed at runtime via PUT /api/v1/admin/chaos.
 */
@Component
public class ChaosSettings {

    private final AtomicReference<String> failMerchantId;

    public ChaosSettings(@Value("${ledger.chaos.fail-merchant-id:}") String initialFailMerchantId) {
        this.failMerchantId = new AtomicReference<>(normalize(initialFailMerchantId));
    }

    public String getFailMerchantId() {
        return failMerchantId.get();
    }

    public void setFailMerchantId(String merchantId) {
        failMerchantId.set(normalize(merchantId));
    }

    public boolean shouldFail(String merchantId) {
        String target = failMerchantId.get();
        return target != null && target.equals(merchantId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
