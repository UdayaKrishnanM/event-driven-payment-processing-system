package com.payments.payment.dto;

import com.payments.common.util.CardMasker;
import com.payments.payment.validation.HasExpiry;
import com.payments.payment.validation.NotExpired;
import com.payments.payment.validation.ValidCard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** POST /api/v1/payments body. Money is always BigDecimal, never double. */
@NotExpired
public record PaymentRequest(
        @Schema(example = "MER-1001")
        @NotBlank(message = "merchantId is required")
        @Size(max = 64, message = "merchantId must be at most 64 characters")
        String merchantId,

        @Schema(example = "4111111111111111")
        @NotBlank(message = "cardNumber is required")
        @ValidCard
        String cardNumber,

        @Schema(example = "12")
        @NotNull(message = "expiryMonth is required")
        @Min(value = 1, message = "expiryMonth must be between 1 and 12")
        @Max(value = 12, message = "expiryMonth must be between 1 and 12")
        Integer expiryMonth,

        @Schema(example = "2028")
        @NotNull(message = "expiryYear is required")
        @Min(value = 2000, message = "expiryYear must be a 4-digit year")
        @Max(value = 2100, message = "expiryYear must be a 4-digit year")
        Integer expiryYear,

        @Schema(example = "2499.00")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
        @DecimalMax(value = "100000.00", message = "amount must be at most 100000")
        @Digits(integer = 6, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @Schema(example = "INR")
        @NotBlank(message = "currency is required")
        @Pattern(regexp = "INR|USD|EUR", message = "currency must be one of INR, USD, EUR")
        String currency) implements HasExpiry {

    /**
     * Stable text form used to hash the request for the idempotency check
     * (2499.0 and 2499.00 are the same payment).
     */
    public String canonicalForm() {
        String normalizedAmount = amount == null ? "null" : amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
        String digits = cardNumber == null ? "null" : cardNumber.replaceAll("\\D", "");
        return String.join("|", merchantId, digits, String.valueOf(expiryMonth), String.valueOf(expiryYear),
                normalizedAmount, currency);
    }

    /** Never print the full card number, even by accident. */
    @Override
    public String toString() {
        return "PaymentRequest[merchantId=" + merchantId + ", card=" + CardMasker.mask(cardNumber)
                + ", expiry=" + expiryMonth + "/" + expiryYear + ", amount=" + amount + ", currency=" + currency + "]";
    }
}
