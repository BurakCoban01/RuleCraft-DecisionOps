package com.rulecraft.decisionops.impact.api;

import com.rulecraft.decisionops.identity.security.CurrentUser;
import com.rulecraft.decisionops.impact.DependencyImpactService;
import com.rulecraft.decisionops.impact.DependencyImpactService.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/decision-services/{serviceId}/dependency-impact")
public class DependencyImpactController {
    private final DependencyImpactService service;
    public DependencyImpactController(DependencyImpactService service) { this.service = service; }

    @GetMapping
    ImpactWorkspace current(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            Authentication authentication) {
        return service.current(CurrentUser.require(authentication), projectId, serviceId);
    }

    @GetMapping("/assessments")
    List<AssessmentSummary> history(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            Authentication authentication) {
        return service.history(CurrentUser.require(authentication), projectId, serviceId);
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentDetail capture(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @RequestBody CaptureCommand command, Authentication authentication) {
        return service.capture(CurrentUser.require(authentication), projectId, serviceId, command);
    }

    @GetMapping("/assessments/{assessmentId}")
    AssessmentDetail assessment(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID assessmentId, Authentication authentication) {
        return service.assessment(CurrentUser.require(authentication), projectId, serviceId, assessmentId);
    }
}
