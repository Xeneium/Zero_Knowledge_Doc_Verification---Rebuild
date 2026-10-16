package com.zkdoc.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zkdoc.api.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("FIND-001 Remediation: Demo Token Security Hardening Tests")
class DemoSecurityHardeningTest {

    @Nested
    @SpringBootTest(classes = Application.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "zkdoc.demo.seed-enabled=false"
    })
    @DisplayName("Default / Disabled Demo Token Mode")
    class DisabledByDefaultTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Anonymous requests to /api/public/demo-tokens return 404 when demo seeding is disabled")
        void demoTokensEndpointReturns404WhenDisabled() throws Exception {
            mockMvc.perform(get("/api/public/demo-tokens"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Production Profile Hardening Tests")
    class ProductionProfileTests {

        @Test
        @DisplayName("DemoDataInitializer fails fast if prod profile is active")
        void demoDataInitializerFailsFastUnderProdProfile() {
            org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
            env.setActiveProfiles("prod");

            assertThatThrownBy(() -> new DemoDataInitializer(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, env
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CRITICAL PRODUCTION SECURITY ERROR");
        }

        @Test
        @DisplayName("DemoTokensController rejects request with 404 under prod profile even if demoEnabled=true")
        void demoTokensControllerRejectsUnderProdProfile() {
            org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
            env.setActiveProfiles("production");

            DemoTokensController controller = new DemoTokensController(
                    org.mockito.Mockito.mock(DemoTokenService.class),
                    env,
                    true
            );

            assertThatThrownBy(controller::getDemoTokens)
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .satisfies(ex -> {
                        org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
                    });
        }
    }

    @Nested
    @SpringBootTest(classes = Application.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "spring.profiles.active=local",
            "zkdoc.demo.seed-enabled=true"
    })
    @DisplayName("Explicit Non-Production Demo Mode")
    class ExplicitDemoModeTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("When explicitly enabled in non-prod, returns 200 with valid accounts")
        void explicitDemoModeReturnsTokens() throws Exception {
            String json = mockMvc.perform(get("/api/public/demo-tokens"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(json).contains(DemoCryptoKeyConstants.ADMIN_SUBJECT);
            assertThat(json).contains(DemoCryptoKeyConstants.ISSUER_SUBJECT);
            assertThat(json).contains(DemoCryptoKeyConstants.HOLDER_SUBJECT);
            assertThat(json).contains(DemoCryptoKeyConstants.VERIFIER_SUBJECT);
        }
    }
}
