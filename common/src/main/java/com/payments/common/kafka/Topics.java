package com.payments.common.kafka;

import java.util.List;

/** Kafka topic names. Constants (not an enum) so they can be used inside annotations. */
public final class Topics {

    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_DECLINED = "payment.declined";
    public static final String PAYMENT_SETTLED = "payment.settled";

    public static final String DLT_SUFFIX = ".DLT";
    /** Regex used by the DLT monitor to subscribe to every dead-letter topic. */
    public static final String DLT_PATTERN = ".*\\.DLT";

    public static final int PARTITIONS = 3;

    public static final List<String> MAIN_TOPICS =
            List.of(PAYMENT_INITIATED, PAYMENT_AUTHORIZED, PAYMENT_DECLINED, PAYMENT_SETTLED);

    private Topics() {
    }

    public static String dltOf(String topic) {
        return topic + DLT_SUFFIX;
    }

    public static boolean isDlt(String topic) {
        return topic != null && topic.endsWith(DLT_SUFFIX);
    }

    /** "payment.authorized.DLT" -> "payment.authorized". Returns the input unchanged if it is not a DLT. */
    public static String originalOf(String dltTopic) {
        return isDlt(dltTopic) ? dltTopic.substring(0, dltTopic.length() - DLT_SUFFIX.length()) : dltTopic;
    }

    /** All 8 topics: 4 main topics and their 4 dead-letter topics. */
    public static List<String> allTopics() {
        return MAIN_TOPICS.stream()
                .flatMap(t -> java.util.stream.Stream.of(t, dltOf(t)))
                .toList();
    }
}
