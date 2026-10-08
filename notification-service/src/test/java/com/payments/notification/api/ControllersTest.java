package com.payments.notification.api;

import com.payments.common.web.ResourceNotFoundException;
import com.payments.notification.dto.FailedEventResponse;
import com.payments.notification.dto.NotificationResponse;
import com.payments.notification.dto.PagedResponse;
import com.payments.notification.service.FailedEventService;
import com.payments.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({NotificationController.class, FailedEventController.class})
class ControllersTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    NotificationService notificationService;
    @MockBean
    FailedEventService failedEventService;

    private final FailedEventResponse failed = new FailedEventResponse(UUID.randomUUID(), "payment.authorized.DLT",
            "payment.authorized", "ledger-service", "k", "{}", "err", 0, 1L, Instant.now(), null, 0);

    @Test
    void listsNotifications() throws Exception {
        var n = new NotificationResponse(UUID.randomUUID(), "MER-1", UUID.randomUUID(), "PAYMENT_SETTLED", "msg",
                Instant.now());
        when(notificationService.list("MER-1", null, 0, 20)).thenReturn(new PagedResponse<>(List.of(n), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/notifications").param("merchantId", "MER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("PAYMENT_SETTLED"));
    }

    @Test
    void listsAndReplaysFailedEvents() throws Exception {
        when(failedEventService.list(0, 20)).thenReturn(new PagedResponse<>(List.of(failed), 0, 20, 1, 1));
        when(failedEventService.get(failed.id())).thenReturn(failed);
        when(failedEventService.replay(failed.id())).thenReturn(failed);

        mvc.perform(get("/api/v1/admin/failed-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalTopic").value("payment.authorized"));
        mvc.perform(get("/api/v1/admin/failed-events/{id}", failed.id())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/failed-events/{id}/replay", failed.id())).andExpect(status().isOk());
    }

    @Test
    void unknownFailedEventIs404() throws Exception {
        when(failedEventService.replay(any())).thenThrow(new ResourceNotFoundException("FAILED_EVENT_NOT_FOUND", "nope"));

        mvc.perform(post("/api/v1/admin/failed-events/{id}/replay", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAILED_EVENT_NOT_FOUND"));
    }
}
