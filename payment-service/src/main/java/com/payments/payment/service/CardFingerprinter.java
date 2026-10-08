package com.payments.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Keyed hash (HMAC-SHA256) of the card number. Lets the velocity rule recognise "the same card" without storing
 * the card number. The secret comes from configuration / an environment variable, never from code in production.
 */
@Component
public class CardFingerprinter {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public CardFingerprinter(@Value("${payments.card.fingerprint-secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String fingerprint(String cardNumber) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            String digits = cardNumber.replaceAll("\\D", "");
            return HexFormat.of().formatHex(mac.doFinal(digits.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not fingerprint card", e);
        }
    }
}
