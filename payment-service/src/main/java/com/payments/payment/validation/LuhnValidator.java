package com.payments.payment.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Luhn (mod 10) check. Null/blank is left to @NotBlank. */
public class LuhnValidator implements ConstraintValidator<ValidCard, String> {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 19;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return isValidLuhn(value);
    }

    public static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{" + MIN_LENGTH + "," + MAX_LENGTH + "}")) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
