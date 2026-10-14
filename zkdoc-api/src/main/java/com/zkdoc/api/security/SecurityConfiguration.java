package com.zkdoc.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            com.zkdoc.api.ratelimit.RateLimitingFilter rateLimitingFilter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.audit.SecurityAuditService auditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) -> {
                            if (auditService != null) {
                                auditService.record(com.zkdoc.api.audit.AuditEventRequest
                                        .builder(com.zkdoc.api.audit.AuditEventType.AUTHENTICATION_FAILURE)
                                        .actor("anonymous", com.zkdoc.api.audit.ActorType.ANONYMOUS_PUBLIC)
                                        .target(com.zkdoc.api.audit.TargetType.SYSTEM, request.getRequestURI())
                                        .action("AUTHENTICATE")
                                        .outcome(com.zkdoc.api.audit.AuditOutcome.FAILURE)
                                        .reasonCode("UNAUTHORIZED")
                                        .correlationId(org.slf4j.MDC.get(com.zkdoc.api.logging.MdcCorrelationIdFilter.MDC_KEY))
                                        .clientIp(request.getRemoteAddr())
                                        .build());
                            }
                            if (zkdocMetrics != null) {
                                zkdocMetrics.recordAuthFailure();
                            }
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required or token invalid\"}");
                        })
                        .accessDeniedHandler((request, response, error) -> {
                            if (auditService != null) {
                                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                                String caller = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
                                com.zkdoc.api.audit.ActorType actorType = (auth != null && auth.isAuthenticated()) ? com.zkdoc.api.audit.ActorType.USER : com.zkdoc.api.audit.ActorType.ANONYMOUS_PUBLIC;
                                auditService.record(com.zkdoc.api.audit.AuditEventRequest
                                        .builder(com.zkdoc.api.audit.AuditEventType.ACCESS_DENIED)
                                        .actor(caller, actorType)
                                        .target(com.zkdoc.api.audit.TargetType.SYSTEM, request.getRequestURI())
                                        .action("ACCESS_CHECK")
                                        .outcome(com.zkdoc.api.audit.AuditOutcome.DENIED)
                                        .reasonCode("FORBIDDEN")
                                        .correlationId(org.slf4j.MDC.get(com.zkdoc.api.logging.MdcCorrelationIdFilter.MDC_KEY))
                                        .clientIp(request.getRemoteAddr())
                                        .build());
                            }
                            if (zkdocMetrics != null) {
                                zkdocMetrics.recordAccessDenied();
                            }
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/health/**", "/actuator/info", "/api/public/**").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/credentials/*/suspend", "/api/credentials/*/resume", "/api/credentials/*/revoke").hasAnyRole("ISSUER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/documents", "/api/credentials", "/api/credentials/issue").hasRole("ISSUER")
                        .requestMatchers("/api/capabilities/**").hasAnyRole("ISSUER", "HOLDER", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/holder/**").hasAnyRole("HOLDER", "ADMIN")
                        .requestMatchers("/api/v2/zkp/credential-predicates/**").hasRole("VERIFIER")
                        .requestMatchers("/api/documents/**", "/api/credentials/**")
                        .hasAnyRole("ISSUER", "HOLDER", "VERIFIER", "ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            ManagedJwtDecoderProvider decoderProvider,
            JwtAuthoritiesConverter authoritiesConverter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.audit.SecurityAuditService auditService) {
        return new JwtAuthenticationFilter(decoderProvider, authoritiesConverter, java.util.Optional.ofNullable(auditService));
    }

    @Bean
    public com.zkdoc.api.ratelimit.RateLimitingFilter rateLimitingFilter(
            com.zkdoc.api.ratelimit.RateLimitService rateLimitService,
            com.zkdoc.api.ratelimit.ClientIpResolver clientIpResolver,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.audit.SecurityAuditService auditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics) {
        return new com.zkdoc.api.ratelimit.RateLimitingFilter(rateLimitService, clientIpResolver, java.util.Optional.ofNullable(auditService), java.util.Optional.ofNullable(zkdocMetrics));
    }

    @Bean
    public java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }
}
