package com.rulecraft.decisionops.platform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Platform")
public class PlatformController {

    private final String buildVersion;

    public PlatformController(@Value("${rulecraft.build.version:0.1.0-dev}") String buildVersion) {
        this.buildVersion = buildVersion;
    }

    @GetMapping
    @Operation(summary = "Read the private workspace platform manifest")
    public ResponseEntity<PlatformManifestResponse> manifest() {
        var response = new PlatformManifestResponse(
                "RuleCraft DecisionOps",
                buildVersion,
                "ready",
                Instant.now());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
