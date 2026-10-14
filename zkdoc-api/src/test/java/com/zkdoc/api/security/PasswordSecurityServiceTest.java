package com.zkdoc.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordSecurityServiceTest {

    private PasswordSecurityService passwordSecurityService;

    @BeforeEach
    void setUp() {
        passwordSecurityService = new PasswordSecurityService();
    }

    @Test
    @DisplayName("encode and match valid strong password")
    void encodeAndMatchValidPassword() {
        String raw = "Correct-Horse-Battery-Staple-2026!";
        String encoded = passwordSecurityService.encode(raw);

        assertThat(encoded).isNotBlank().startsWith("$2a$12$");
        assertThat(passwordSecurityService.matches(raw, encoded)).isTrue();
        assertThat(passwordSecurityService.matches("WrongPassword-12345!", encoded)).isFalse();
    }

    @Test
    @DisplayName("reject password shorter than 12 characters")
    void rejectShortPassword() {
        assertThatThrownBy(() -> passwordSecurityService.validatePassword("Short1!", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum length requirement of 12");
    }

    @Test
    @DisplayName("reject password longer than 128 characters")
    void rejectOverlyLongPassword() {
        String excessivelyLong = "A".repeat(129);
        assertThatThrownBy(() -> passwordSecurityService.validatePassword(excessivelyLong, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum length limit of 128");
    }

    @Test
    @DisplayName("reject common dictionary passwords")
    void rejectCommonDictionaryPasswords() {
        assertThatThrownBy(() -> passwordSecurityService.validatePassword("password1234", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too common or easily guessable");

        assertThatThrownBy(() -> passwordSecurityService.validatePassword("administrator", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too common or easily guessable");
    }

    @Test
    @DisplayName("reject password containing username")
    void rejectPasswordContainingUsername() {
        assertThatThrownBy(() -> passwordSecurityService.validatePassword("alice_secret_password_2026", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain the username");
    }

    @Test
    @DisplayName("normalize Unicode NFKC compatibility equivalents")
    void normalizeUnicodeNfkc() {
        // Unicode character \uFB01 is 'fi' ligature, should normalize to "fi" in NFKC
        String ligature = "\uFB01le-security-key-2026!";
        String normalized = passwordSecurityService.normalize(ligature);
        assertThat(normalized).startsWith("fi");

        String encoded = passwordSecurityService.encode(ligature);
        // Matching with decomposed/canonical equivalent should match
        assertThat(passwordSecurityService.matches("file-security-key-2026!", encoded)).isTrue();
    }

    @Test
    @DisplayName("sanitized error message on null input")
    void sanitizedNullInput() {
        assertThatThrownBy(() -> passwordSecurityService.validatePassword(null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password cannot be null");
    }
}
