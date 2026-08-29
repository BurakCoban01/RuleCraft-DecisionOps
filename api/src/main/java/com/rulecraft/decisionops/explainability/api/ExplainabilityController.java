package com.rulecraft.decisionops.explainability.api;

import com.rulecraft.decisionops.explainability.ExplainabilityService;
import com.rulecraft.decisionops.explainability.ExplainabilityService.*;
import com.rulecraft.decisionops.identity.security.CurrentUser;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/decision-services/{serviceId}/decision-traces")
public class ExplainabilityController {
    private final ExplainabilityService service;

    public ExplainabilityController(ExplainabilityService service) { this.service = service; }

    @GetMapping
    List<TraceSummary> history(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query, Authentication authentication) {
        return service.history(CurrentUser.require(authentication), projectId, serviceId, channel, status, query);
    }

    @GetMapping("/{evaluationId}")
    TraceDetail detail(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID evaluationId, Authentication authentication) {
        return service.detail(CurrentUser.require(authentication), projectId, serviceId, evaluationId);
    }
}
