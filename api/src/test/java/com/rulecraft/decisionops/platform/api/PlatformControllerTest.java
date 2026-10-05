package com.rulecraft.decisionops.platform.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesTruthfulPlatformManifestWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/platform").header("X-Correlation-Id", "acceptance-1234"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "acceptance-1234"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(jsonPath("$.product").value("RuleCraft DecisionOps"))
                .andExpect(jsonPath("$.status").value("ready"));
    }

    @Test
    void deniesUnspecifiedRoutes() throws Exception {
        mockMvc.perform(post("/api/v1/platform/admin").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesLivenessWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void publishesTheVersionedOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("RuleCraft DecisionOps API"))
                .andExpect(jsonPath("$.paths['/api/v1/platform']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/sandbox/evaluations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/tests'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/tests/{testId}/runs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/regression-matrices'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/regression-matrices/{runId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/versions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/versions/working'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/versions/diff'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/change-requests'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/change-requests/{requestId}/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/change-requests/{requestId}/promote'].post").exists());
    }
}
