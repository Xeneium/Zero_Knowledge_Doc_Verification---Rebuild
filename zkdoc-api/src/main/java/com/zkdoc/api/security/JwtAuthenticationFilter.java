package com.zkdoc.api.security;

import com.nimbusds.jwt.SignedJWT;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.RegisteredKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates a bearer token only when its registered JWT key is active or retired. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_ROLES = Set.of("ISSUER", "HOLDER", "VERIFIER", "ADMIN");
    private final KeyRegistryService keyRegistryService;

    public JwtAuthenticationFilter(KeyRegistryService keyRegistryService) {
        this.keyRegistryService = keyRegistryService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring("Bearer ".length());
            String keyId = SignedJWT.parse(token).getHeader().getKeyID();
            RegisteredKey key = keyRegistryService.verificationKey(keyId, KeyPurpose.JWT_SIGNING)
                    .orElseThrow(() -> new IllegalArgumentException("JWT key is unknown or revoked"));
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(parseRsaPublicKey(key.publicKeyPem())).build();
            decoder.setJwtValidator(JwtValidators.createDefault());
            Jwt jwt = decoder.decode(token);
            List<SimpleGrantedAuthority> authorities = roleNames(jwt).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(jwt.getSubject(), token, authorities));
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
        }
    }

    private Stream<String> roleNames(Jwt jwt) {
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> roleCollection) {
            return roleCollection.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(ALLOWED_ROLES::contains);
        }
        if (roles instanceof String role) {
            return ALLOWED_ROLES.contains(role) ? Stream.of(role) : Stream.empty();
        }
        return Stream.empty();
    }

    private RSAPublicKey parseRsaPublicKey(String pem) throws Exception {
        String encoded = pem.replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }
}
