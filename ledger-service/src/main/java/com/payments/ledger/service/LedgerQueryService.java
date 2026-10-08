package com.payments.ledger.service;

import com.payments.ledger.domain.Accounts;
import com.payments.ledger.domain.Direction;
import com.payments.ledger.domain.LedgerEntry;
import com.payments.ledger.domain.LedgerEntryRepository;
import com.payments.ledger.domain.LedgerQueries;
import com.payments.ledger.dto.LedgerEntryResponse;
import com.payments.ledger.dto.MerchantBalanceResponse;
import com.payments.ledger.dto.PaymentEntriesResponse;
import com.payments.ledger.dto.TrialBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerQueryService {

    private final LedgerEntryRepository entryRepository;
    private final LedgerQueries ledgerQueries;

    public PaymentEntriesResponse entriesForPayment(UUID paymentId) {
        List<LedgerEntry> entries = entryRepository.findByPaymentIdOrderByDirectionAsc(paymentId);
        return new PaymentEntriesResponse(paymentId, entries.stream().map(LedgerEntryResponse::from).toList(),
                isBalanced(entries));
    }

    public MerchantBalanceResponse merchantBalance(String merchantId) {
        String account = Accounts.merchant(merchantId);
        return new MerchantBalanceResponse(merchantId, account, ledgerQueries.balancesForAccount(account));
    }

    public TrialBalance trialBalance() {
        return ledgerQueries.trialBalance();
    }

    static boolean isBalanced(List<LedgerEntry> entries) {
        BigDecimal debits = sum(entries, Direction.DEBIT);
        BigDecimal credits = sum(entries, Direction.CREDIT);
        return debits.compareTo(credits) == 0;
    }

    private static BigDecimal sum(List<LedgerEntry> entries, Direction direction) {
        return entries.stream().filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
