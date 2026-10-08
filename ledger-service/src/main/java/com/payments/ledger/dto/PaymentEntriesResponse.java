package com.payments.ledger.dto;

import java.util.List;
import java.util.UUID;

public record PaymentEntriesResponse(UUID paymentId, List<LedgerEntryResponse> entries, boolean balanced) {
}
