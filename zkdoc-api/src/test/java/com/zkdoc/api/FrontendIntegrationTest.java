package com.zkdoc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class FrontendIntegrationTest {
    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @Test
    void rootServesTheFrontendEntryPoint() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/verify",
            "/credentials",
            "/verification",
            "/predicate-verification",
            "/audit-logs",
            "/administration"
    })
    void frontendDeepLinkServesTheFrontendEntryPoint(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void unknownApiPathIsNotRewrittenToTheFrontend() throws Exception {
        mockMvc.perform(get("/api/not-a-route"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointRemainsPublicJson() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-frontend-it-").resolve("zkdoc-frontend.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}
