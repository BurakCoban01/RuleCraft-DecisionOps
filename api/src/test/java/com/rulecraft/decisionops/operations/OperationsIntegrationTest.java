package com.rulecraft.decisionops.operations;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.DecisionServiceCommand;
import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import com.rulecraft.decisionops.identity.domain.*;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.operations.OperationsService.RecoveryCommand;
import com.rulecraft.decisionops.platform.security.AbuseProtectionService;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.simulation.domain.*;
import com.rulecraft.decisionops.simulation.persistence.RegressionMatrixRunRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OperationsIntegrationTest {
    @Autowired OperationsService operations; @Autowired ProjectService projects; @Autowired CatalogService catalog;
    @Autowired RegressionMatrixRunRepository matrices;
    @Autowired UserAccountRepository users; @Autowired PasswordEncoder passwordEncoder; @Autowired Clock clock;
    @Autowired MockMvc mockMvc; @Autowired AbuseProtectionService abuseProtection;
    RuleCraftPrincipal admin;

    @BeforeEach void actor() { admin = new RuleCraftPrincipal(
            users.findForLogin("acme-rules", "admin@example.test").orElseThrow(), "acme-rules"); }

    @Test void repairsTenantScopedInterruptedWorkAndPersistsIdempotentEvidence() {
        var project = projects.create(admin, key("OPS"), "Operational recovery", null);
        var service = catalog.createService(admin, project.id(), new DecisionServiceCommand(
                key("SERVICE"), "Recovery fixture", null, List.of(),
                List.of(new CatalogService.FieldCommand("result", "Result",
                        new CatalogService.TypeRefCommand(BuiltinType.BOOLEAN, null), true)), null));
        var now = Instant.now(clock);
        var abandoned = matrices.saveAndFlush(new RegressionMatrixRun(admin.tenantId(), project.id(), service.id(),
                UUID.randomUUID(), "[\"abandoned-case\"]", 1, admin.userId(), now));
        var before = operations.workspace(admin);
        assertThat(before.posture().status()).isEqualTo("DEGRADED");
        assertThat(before.posture().runningMatrices()).isEqualTo(1);
        assertThat(before.posture().inconsistentRuntimePointers()).isZero();
        assertThat(before.posture().schemaVersion()).isEqualTo("16");
        var request = new RecoveryCommand(UUID.randomUUID(), before.posture().fingerprint());
        var recovered = operations.recover(admin, request);
        assertThat(recovered.interruptedMatrices()).isEqualTo(1);
        assertThat(recovered.clearedRuntimePointers()).isZero();
        assertThat(recovered.status()).isEqualTo("READY");
        assertThat(operations.recover(admin, request).id()).isEqualTo(recovered.id());
        assertThat(matrices.findById(abandoned.getId()).orElseThrow().getStatus()).isEqualTo(MatrixRunStatus.INTERRUPTED);
        assertThat(operations.workspace(admin).recoveries()).extracting("id").contains(recovered.id());
        assertCode("RC-RECOVERY-REQUEST-CONFLICT", () -> operations.recover(admin,
                new RecoveryCommand(request.clientRequestId(), "0".repeat(64))));
        assertCode("RC-RECOVERY-STALE", () -> operations.recover(admin,
                new RecoveryCommand(UUID.randomUUID(), before.posture().fingerprint())));
    }

    @Test void enforcesAdminBoundaryPublishesContractAndHardenedHeaders() throws Exception {
        var memberAccount = users.save(new UserAccount(admin.tenantId(), "ops-member" + UUID.randomUUID() + "@example.test",
                "Olivia Operator", passwordEncoder.encode("CorrectHorseBattery91!"), TenantRole.MEMBER, Instant.now(clock)));
        var member = new RuleCraftPrincipal(memberAccount, "acme-rules");
        assertCode("RC-OPERATIONS-ADMIN-REQUIRED", () -> operations.workspace(member));
        mockMvc.perform(get("/api/v1/operations").with(user(member)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RC-OPERATIONS-ADMIN-REQUIRED"));
        mockMvc.perform(get("/api/v1/operations").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posture.schemaVersion").value("16"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")));
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/operations']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/operations/recoveries']").exists());
    }

    @Test void throttlesFailedLoginAndRuntimeCredentialWindowsWithStableEvidence() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var body = "{\"workspace\":\"missing-" + suffix + "\",\"email\":\"nobody-" + suffix
                + "@example.test\",\"password\":\"WrongPassword123!\"}";
        for (var index = 0; index < 5; index++) mockMvc.perform(post("/api/v1/session/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/session/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RC-AUTH-RATE-LIMIT"));
        var response = new MockHttpServletResponse();
        for (var index = 0; index < 120; index++) assertThat(abuseProtection.allowRuntime(suffix, response)).isTrue();
        assertThat(abuseProtection.allowRuntime(suffix, response)).isFalse();
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(abuseProtection.loginDenials()).isPositive();
        assertThat(abuseProtection.runtimeDenials()).isPositive();
    }

    private String key(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(); }
    private void assertCode(String code, Runnable action) { assertThatThrownBy(action::run)
            .isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code); }
}
