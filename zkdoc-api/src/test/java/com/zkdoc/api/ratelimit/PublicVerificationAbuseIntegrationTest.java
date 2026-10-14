package com.zkdoc.api.ratelimit;

import com.zkdoc.api.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class PublicVerificationAbuseIntegrationTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BucketRateLimitService bucketRateLimitService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @Test
    @DisplayName("public verification endpoint throttles repeated scanning beyond capacity")
    void throttlesRepeatedScanning() throws Exception {
        bucketRateLimitService.clear();
        String fakeToken = "random-enumeration-attempt-token-12345";
        String clientIp = "198.51.100.42";

        RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "ip:" + clientIp);
        int limit = RateLimitCategory.PUBLIC_VERIFICATION.getCapacity();

        // Consume limit - 1 tokens directly on the service
        for (int i = 0; i < limit - 1; i++) {
            RateLimitDecision preDecision = bucketRateLimitService.tryAcquire(key);
            org.junit.jupiter.api.Assertions.assertTrue(preDecision.allowed());
        }

        // The limit-th attempt succeeds through the filter chain (returns 404 since token does not exist)
        mockMvc.perform(get("/api/public/verify/" + fakeToken)
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        }))
                .andExpect(status().isNotFound());

        // The (limit + 1)-th attempt must be rejected by RateLimitingFilter with HTTP 429 Too Many Requests
        mockMvc.perform(get("/api/public/verify/" + fakeToken)
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.category").value("PUBLIC_VERIFICATION"));
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-pub-abuse-it-").resolve("zkdoc-pub-abuse.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}
