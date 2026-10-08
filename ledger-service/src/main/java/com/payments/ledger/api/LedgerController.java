package com.payments.ledger.api;

import com.payments.ledger.dto.MerchantBalanceResponse;
import com.payments.ledger.dto.PaymentEntriesResponse;
import com.payments.ledger.dto.TrialBalance;
import com.payments.ledger.service.LedgerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger")
public class LedgerController {

    private final LedgerQueryService ledgerQueryService;

    @Operation(summary = "Debit and credit entries for one payment (2 per settled payment)")
    @GetMapping("/payments/{paymentId}/entries")
    public PaymentEntriesResponse entries(@PathVariable UUID paymentId) {
        return ledgerQueryService.entriesForPayment(paymentId);
    }

    @Operation(summary = "Merchant settled balance per currency (credits - debits)")
    @GetMapping("/merchants/{merchantId}/balance")
    public MerchantBalanceResponse balance(@PathVariable String merchantId) {
        return ledgerQueryService.merchantBalance(merchantId);
    }

    @Operation(summary = "Total debits vs total credits across the whole ledger (must always balance)")
    @GetMapping("/trial-balance")
    public TrialBalance trialBalance() {
        return ledgerQueryService.trialBalance();
    }
}
