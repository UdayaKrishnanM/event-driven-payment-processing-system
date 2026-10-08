package com.payments.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.payments.common.util.CardMasker;

/**
 * Logback converter used in logback-spring.xml as {@code %maskedMsg}.
 * Last line of defence: even if code accidentally logs a card number, it is masked before it reaches the log.
 */
public class CardMaskingConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return CardMasker.maskPans(event.getFormattedMessage());
    }
}
