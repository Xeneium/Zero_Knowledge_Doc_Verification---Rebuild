package com.zkdoc.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zkdoc.api.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@DisplayName("FIND-008 Remediation: Security Headers & CSP Enforcement Tests")
class SecurityHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Content-Security-Policy header is strictly configured without 'unsafe-inline' in style-src")
    void contentSecurityPolicyIsStrictlyHardened() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).isNotNull();
        assertThat(csp).contains("default-src 'self'");
        assertThat(csp).contains("script-src 'self'");
        assertThat(csp).contains("style-src 'self'");
        assertThat(csp).doesNotContain("'unsafe-inline'");
        assertThat(csp).contains("frame-ancestors 'none'");
        assertThat(csp).contains("object-src 'none'");
        assertThat(csp).contains("base-uri 'self'");
        assertThat(csp).contains("form-action 'self'");
    }

    @Test
    @DisplayName("Critical OWASP anti-clickjacking and sniffing headers are present")
    void defensiveHeadersArePresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
