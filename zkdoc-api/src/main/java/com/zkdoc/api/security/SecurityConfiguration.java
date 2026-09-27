package com.zkdoc.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) -> response.sendError(401, "Authentication required")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/documents", "/api/credentials").hasRole("ISSUER")
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/qr").hasRole("ISSUER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v2/zkp/credential-predicates/**").hasRole("VERIFIER")
                        .requestMatchers("/api/documents/**", "/api/credentials/**")
                        .hasAnyRole("ISSUER", "HOLDER", "VERIFIER", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
