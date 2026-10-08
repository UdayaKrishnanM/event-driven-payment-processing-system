package com.payments.payment.authorization;

import java.security.SecureRandom;
import java.util.function.Supplier;

/** 6-character uppercase alphanumeric approval code, e.g. "A1B2C3". */
public class AuthCodeGenerator implements Supplier<String> {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String get() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
