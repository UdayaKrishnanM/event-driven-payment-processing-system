package com.payments.payment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardFingerprinterTest {

    @Test
    void sameCardSameFingerprintAndNeverTheCardItself() {
        var f = new CardFingerprinter("secret");
        String a = f.fingerprint("4111111111111111");

        assertThat(a).hasSize(64).doesNotContain("4111111111111111");
        assertThat(f.fingerprint("4111 1111 1111 1111")).isEqualTo(a);
        assertThat(f.fingerprint("5555555555554444")).isNotEqualTo(a);
    }

    @Test
    void differentSecretGivesDifferentFingerprint() {
        assertThat(new CardFingerprinter("s1").fingerprint("4111111111111111"))
                .isNotEqualTo(new CardFingerprinter("s2").fingerprint("4111111111111111"));
    }
}
