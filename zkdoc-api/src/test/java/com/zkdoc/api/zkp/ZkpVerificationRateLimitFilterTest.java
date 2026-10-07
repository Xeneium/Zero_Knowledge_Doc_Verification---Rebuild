package com.zkdoc.api.zkp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZkpVerificationRateLimitFilterTest {

    @Test
    @DisplayName("rate limiter throttles after capacity is exhausted for a user")
    void throttlesAfterCapacityExhausted() throws Exception {
        ZkpVerificationRateLimitFilter filter = new ZkpVerificationRateLimitFilter();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("verifier-user", "pass", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", ZkpVerificationRateLimitFilter.ZKP_VERIFY_PATH);
        request.setRemoteAddr("192.168.1.100");

        for (int i = 0; i < ZkpVerificationRateLimitFilter.CAPACITY; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();
            filter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus(), "Request " + i + " should succeed");
        }

        // 31st request should be rejected with HTTP 429
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilter(request, rejectedResponse, filterChain);

        assertEquals(429, rejectedResponse.getStatus());
        assertEquals("60", rejectedResponse.getHeader("Retry-After"));
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("rate limiter ignores non-ZKP verify paths")
    void ignoresOtherPaths() throws Exception {
        ZkpVerificationRateLimitFilter filter = new ZkpVerificationRateLimitFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/other/path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);
        assertEquals(200, response.getStatus());
    }
}
