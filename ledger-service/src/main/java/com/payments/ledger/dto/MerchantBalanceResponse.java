package com.payments.ledger.dto;

import java.util.List;

public record MerchantBalanceResponse(String merchantId, String account, List<CurrencyBalance> balances) {
}
