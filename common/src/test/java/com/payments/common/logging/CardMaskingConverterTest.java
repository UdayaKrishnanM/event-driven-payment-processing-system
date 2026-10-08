package com.payments.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardMaskingConverterTest {

    @Test
    void logLinesNeverContainTheFullCardNumber() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("received card 4111111111111111");

        String out = new CardMaskingConverter().convert(event);

        assertThat(out).isEqualTo("received card **** 1111").doesNotContain("4111111111111111");
    }
}
