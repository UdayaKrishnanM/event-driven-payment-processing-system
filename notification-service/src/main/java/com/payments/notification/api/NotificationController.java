package com.payments.notification.api;

import com.payments.notification.dto.NotificationResponse;
import com.payments.notification.dto.PagedResponse;
import com.payments.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List notifications (filter by merchantId or paymentId), newest first")
    @GetMapping
    public PagedResponse<NotificationResponse> list(@RequestParam(required = false) String merchantId,
                                                    @RequestParam(required = false) UUID paymentId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(merchantId, paymentId, page, size);
    }
}
