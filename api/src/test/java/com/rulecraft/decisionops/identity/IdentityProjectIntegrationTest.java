package com.rulecraft.decisionops.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rulecraft.decisionops.identity.domain.Tenant;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.TenantRepository;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityProjectIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenants;
    @Autowired UserAccountRepository users;
    @Autowired ProjectService projects;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;
    @Autowired JdbcClient jdbc;

    @Test
    void createsDatabaseBackedSessionAndPersistsProject() throws Exception {
        mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/session/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspace":"acme-rules","email":"admin@example.test",
                                 "password":"CorrectHorseBattery12!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.tenantRole").value("ADMIN"))
                .andReturn().getResponse();

        var sessionCookie = login.getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        var key = "T" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        mockMvc.perform(post("/api/v1/projects").cookie(sessionCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"" + key + "\",\"name\":\"Pricing governance\","
                                + "\"description\":\"Decision ownership and review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value(key))
                .andExpect(jsonPath("$.accessRole").value("OWNER"));

        mockMvc.perform(get("/api/v1/projects").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == '" + key + "')]").exists());
        assertThat(jdbc.sql("select count(*) from spring_session").query(Long.class).single()).isPositive();
        assertThat(jdbc.sql("select count(*) from audit_event where action = 'PROJECT_CREATED'")
                .query(Long.class).single()).isPositive();
    }

    @Test
    void rejectsInvalidLoginWithStableProblemCode() throws Exception {
        mockMvc.perform(post("/api/v1/session/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspace":"acme-rules","email":"admin@example.test","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RC-AUTH-INVALID"));
    }

    @Test
    void enforcesTenantIsolationAndLastOwnerInvariant() {
        var account = users.findForLogin("acme-rules", "admin@example.test").orElseThrow();
        var actor = new RuleCraftPrincipal(account, "acme-rules");
        var first = projects.create(actor, "I" + UUID.randomUUID().toString().substring(0, 7),
                "Isolated project", null);

        var now = Instant.now(clock);
        var otherTenant = tenants.save(new Tenant("other-rules", "Other Rules", now));
        var otherAccount = users.save(new UserAccount(otherTenant.getId(), "owner@other.test", "Other Owner",
                passwordEncoder.encode("CorrectHorseBattery34!"), TenantRole.ADMIN, now));
        var other = new RuleCraftPrincipal(otherAccount, "other-rules");

        assertThatThrownBy(() -> projects.get(other, first.id()))
                .isInstanceOf(DomainProblemException.class)
                .extracting("code").isEqualTo("RC-PROJECT-NOT-FOUND");
        assertThatThrownBy(() -> projects.removeMember(actor, first.id(), actor.userId()))
                .isInstanceOf(DomainProblemException.class)
                .extracting("code").isEqualTo("RC-PROJECT-LAST-OWNER");
    }
}
