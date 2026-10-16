package com.zkdoc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@DisplayName("DEF-F11-001 Remediation: SPA Direct Browser Route Forwarding Tests")
class SpaForwardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "Client route {0} forwards to index.html")
    @ValueSource(strings = {
            "/",
            "/login",
            "/verify",
            "/credentials",
            "/verification",
            "/predicate-verification",
            "/audit-logs",
            "/administration",
            "/admin",
            "/issuer",
            "/holder",
            "/forbidden"
    })
    void clientRoutesForwardToIndexHtml(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
