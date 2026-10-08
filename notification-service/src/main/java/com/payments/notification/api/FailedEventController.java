package com.payments.notification.api;

import com.payments.notification.dto.FailedEventResponse;
import com.payments.notification.dto.PagedResponse;
import com.payments.notification.service.FailedEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/failed-events")
@RequiredArgsConstructor
@Tag(name = "Admin - failed events (DLT)")
public class FailedEventController {

    private final FailedEventService failedEventService;

    @Operation(summary = "Messages that landed on a *.DLT topic, newest first")
    @GetMapping
    public PagedResponse<FailedEventResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return failedEventService.list(page, size);
    }

    @GetMapping("/{id}")
    public FailedEventResponse get(@PathVariable UUID id) {
        return failedEventService.get(id);
    }

    @Operation(summary = "Republish a failed message to its original topic")
    @PostMapping("/{id}/replay")
    public FailedEventResponse replay(@PathVariable UUID id) {
        return failedEventService.replay(id);
    }
}
