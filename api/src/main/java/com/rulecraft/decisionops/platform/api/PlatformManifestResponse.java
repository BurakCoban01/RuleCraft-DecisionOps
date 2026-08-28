package com.rulecraft.decisionops.platform.api;

import java.time.Instant;

public record PlatformManifestResponse(
        String product,
        String version,
        String status,
        Instant serverTime) {
}
