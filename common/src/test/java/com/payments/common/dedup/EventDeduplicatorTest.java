package com.payments.common.dedup;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventDeduplicatorTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EventDeduplicator deduplicator = new EventDeduplicator(jdbc);

    @Test
    void firstDeliveryInsertsOneRow() {
        UUID id = UUID.randomUUID();
        when(jdbc.update(eq(EventDeduplicator.INSERT_SQL), eq(id))).thenReturn(1);

        assertThat(deduplicator.firstDelivery(id)).isTrue();
    }

    @Test
    void duplicateInsertsNothingAndIsSkipped() {
        UUID id = UUID.randomUUID();
        when(jdbc.update(eq(EventDeduplicator.INSERT_SQL), eq(id))).thenReturn(0);

        assertThat(deduplicator.firstDelivery(id)).isFalse();
    }
}
