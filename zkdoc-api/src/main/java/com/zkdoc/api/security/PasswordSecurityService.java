package com.zkdoc.api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;

/**
 * Service providing password security baselines:
 * - Modern adaptive password hashing (BCrypt with strength 12)
 * - Strict length bounds (12 to 128 characters)
 * - Unicode NFKC normalization
 * - Protection against common dictionary passwords
 * - Username proximity/similarity checks
 * - Sanitized failure logging/exception messages that never leak password bytes
 */
@Service
public class PasswordSecurityService {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    private static final int BCRYPT_STRENGTH = 12;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1234",
            "password12345",
            "administrator",
            "changeme1234",
            "welcome12345",
            "letmein12345",
            "qwertyuiop12",
            "123456789012"
    );

    private final PasswordEncoder passwordEncoder;

    public PasswordSecurityService() {
        this.passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    public PasswordSecurityService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    public String encode(CharSequence rawPassword) {
        validatePassword(rawPassword, null);
        String normalized = normalize(rawPassword);
        return passwordEncoder.encode(normalized);
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String normalized = normalize(rawPassword);
        return passwordEncoder.matches(normalized, encodedPassword);
    }

    public void validatePassword(CharSequence rawPassword, String username) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }

        String normalized = normalize(rawPassword);

        if (normalized.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password does not meet minimum length requirement of " + MIN_LENGTH + " characters");
        }

        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password exceeds maximum length limit of " + MAX_LENGTH + " characters");
        }

        String lower = normalized.toLowerCase();
        if (COMMON_PASSWORDS.contains(lower)) {
            throw new IllegalArgumentException("Password is too common or easily guessable");
        }

        if (username != null && !username.isBlank()) {
            String normUser = username.trim().toLowerCase();
            if (lower.contains(normUser)) {
                throw new IllegalArgumentException("Password must not contain the username or account identifier");
            }
        }
    }

    public String normalize(CharSequence rawPassword) {
        if (rawPassword == null) {
            return "";
        }
        return Normalizer.normalize(rawPassword, Normalizer.Form.NFKC);
    }
}
