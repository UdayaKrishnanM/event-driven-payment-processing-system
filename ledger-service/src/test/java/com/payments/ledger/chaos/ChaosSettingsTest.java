package com.payments.ledger.chaos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosSettingsTest {

    @Test
    void offByDefault() {
        var chaos = new ChaosSettings("");
        assertThat(chaos.getFailMerchantId()).isNull();
        assertThat(chaos.shouldFail("MER-1")).isFalse();
    }

    @Test
    void failsOnlyTheConfiguredMerchantAndCanBeTurnedOff() {
        var chaos = new ChaosSettings(" MER-DLT ");
        assertThat(chaos.shouldFail("MER-DLT")).isTrue();
        assertThat(chaos.shouldFail("MER-1")).isFalse();

        chaos.setFailMerchantId(null);
        assertThat(chaos.shouldFail("MER-DLT")).isFalse();
    }

    @Test
    void exceptionNamesTheMerchant() {
        assertThat(new SimulatedLedgerFailureException("MER-X")).hasMessageContaining("MER-X");
    }
}
