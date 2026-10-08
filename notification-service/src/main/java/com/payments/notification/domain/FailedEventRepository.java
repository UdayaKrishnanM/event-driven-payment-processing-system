package com.payments.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailedEventRepository extends JpaRepository<FailedEvent, UUID> {

    boolean existsByTopicAndDltPartitionAndDltOffset(String topic, int dltPartition, long dltOffset);

    Page<FailedEvent> findAllByOrderByFailedAtDesc(Pageable pageable);
}
