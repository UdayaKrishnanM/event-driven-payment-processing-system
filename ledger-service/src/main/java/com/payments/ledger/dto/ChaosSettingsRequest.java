package com.payments.ledger.dto;

/** failMerchantId = payments from this merchant fail in the ledger consumer (null/blank turns it off). */
public record ChaosSettingsRequest(String failMerchantId) {
}
