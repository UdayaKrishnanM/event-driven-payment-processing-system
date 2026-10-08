package com.payments.ledger.api;

import com.payments.ledger.chaos.ChaosSettings;
import com.payments.ledger.dto.ChaosSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Demo-only failure injection. In a real deployment this would be disabled or behind admin auth. */
@RestController
@RequestMapping("/api/v1/admin/chaos")
@RequiredArgsConstructor
@Tag(name = "Admin - failure injection (demo)")
public class ChaosController {

    private final ChaosSettings chaosSettings;

    @Operation(summary = "Show which merchant (if any) currently fails in the ledger consumer")
    @GetMapping
    public Map<String, Object> get() {
        return view();
    }

    @Operation(summary = "Make the ledger consumer fail for one merchant (send null/blank to turn off)")
    @PutMapping
    public Map<String, Object> set(@RequestBody ChaosSettingsRequest request) {
        chaosSettings.setFailMerchantId(request.failMerchantId());
        return view();
    }

    private Map<String, Object> view() {
        Map<String, Object> body = new HashMap<>();
        body.put("failMerchantId", chaosSettings.getFailMerchantId());
        body.put("enabled", chaosSettings.getFailMerchantId() != null);
        return body;
    }
}
