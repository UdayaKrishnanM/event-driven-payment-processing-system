package com.payments.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Card-data safety helpers. The full card number (PAN) must never be stored or logged:
 * only "**** " + last 4 digits.
 */
public final class CardMasker {

    /** 13-19 consecutive digits looks like a PAN. */
    private static final Pattern PAN_PATTERN = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");
    private static final String MASK_PREFIX = "**** ";

    private CardMasker() {
    }

    /** "4111111111111111" -> "**** 1111". Non-digits (spaces, dashes) are ignored. */
    public static String mask(String cardNumber) {
        if (cardNumber == null) {
            return null;
        }
        String digits = cardNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "****";
        }
        return MASK_PREFIX + digits.substring(digits.length() - 4);
    }

    /** Last 4 digits of a masked or unmasked card number. */
    public static String last4(String card) {
        if (card == null) {
            return null;
        }
        String digits = card.replaceAll("\\D", "");
        return digits.length() < 4 ? digits : digits.substring(digits.length() - 4);
    }

    /** Replaces every PAN-looking number inside free text (log lines, error messages) with its masked form. */
    public static String maskPans(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = PAN_PATTERN.matcher(text);
        if (!m.find()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(mask(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
