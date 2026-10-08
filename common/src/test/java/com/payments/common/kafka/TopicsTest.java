package com.payments.common.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicsTest {

    @Test
    void thereAreEightTopicsFourMainAndFourDlt() {
        assertThat(Topics.allTopics()).hasSize(8)
                .contains("payment.initiated", "payment.initiated.DLT", "payment.settled.DLT");
    }

    @Test
    void dltNamesRoundTrip() {
        assertThat(Topics.dltOf("payment.authorized")).isEqualTo("payment.authorized.DLT");
        assertThat(Topics.isDlt("payment.authorized.DLT")).isTrue();
        assertThat(Topics.isDlt("payment.authorized")).isFalse();
        assertThat(Topics.isDlt(null)).isFalse();
        assertThat(Topics.originalOf("payment.authorized.DLT")).isEqualTo("payment.authorized");
        assertThat(Topics.originalOf("payment.authorized")).isEqualTo("payment.authorized");
        assertThat("payment.declined.DLT").matches(Topics.DLT_PATTERN);
        assertThat("payment.declined").doesNotMatch(Topics.DLT_PATTERN);
    }
}
