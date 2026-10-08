package com.payments.notification.messaging;

import com.payments.common.kafka.Topics;
import com.payments.notification.config.AppConfig;
import com.payments.notification.service.FailedEventService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Listens to every *.DLT topic and stores each message in failed_events. */
@Component
@RequiredArgsConstructor
public class DeadLetterMonitor {

    private final FailedEventService failedEventService;

    @KafkaListener(topicPattern = Topics.DLT_PATTERN, groupId = "dlt-monitor",
            containerFactory = AppConfig.DLT_CONTAINER_FACTORY)
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        failedEventService.store(record);
    }
}
