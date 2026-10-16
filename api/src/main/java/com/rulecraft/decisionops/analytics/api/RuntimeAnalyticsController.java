package com.rulecraft.decisionops.analytics.api;

import com.rulecraft.decisionops.analytics.RuntimeAnalyticsService;
import com.rulecraft.decisionops.analytics.RuntimeAnalyticsService.*;
import com.rulecraft.decisionops.identity.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/decision-services/{serviceId}/runtime-analytics")
public class RuntimeAnalyticsController {
    private final RuntimeAnalyticsService service;
    public RuntimeAnalyticsController(RuntimeAnalyticsService service) { this.service = service; }

    @GetMapping
    AnalyticsWorkspace workspace(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @RequestParam(defaultValue = "HOURS_24") String window,
            @RequestParam(defaultValue = "ALL") String channel, Authentication authentication) {
        return service.workspace(CurrentUser.require(authentication), projectId, serviceId, window, channel);
    }

    @PostMapping("/benchmarks")
    @ResponseStatus(HttpStatus.CREATED)
    BenchmarkView benchmark(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @RequestBody BenchmarkCommand command, Authentication authentication) {
        return service.benchmark(CurrentUser.require(authentication), projectId, serviceId, command);
    }
}
