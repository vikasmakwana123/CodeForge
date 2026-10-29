package com.example.CodeForge.service;

import com.example.CodeForge.dto.AuthResponse;
import com.example.CodeForge.models.User;
import com.example.CodeForge.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void registrationHashesPasswordAndIssuesTokenAcceptedByLogin() {
        AuthResponse registration = authService.register("Test User", "Test@Example.com", "password-123");

        User user = users.findById(registration.user().id()).orElseThrow();
        assertNotEquals("password-123", user.getPasswordHash());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test User", registration.user().firstName() + " " + registration.user().lastName());
        assertEquals(user.getId().toString(), jwtDecoder.decode(registration.accessToken()).getSubject());
        assertTrue(passwordEncoder.matches("password-123", user.getPasswordHash()));

        AuthResponse login = authService.login("test@example.com", "password-123");
        assertEquals(user.getId(), login.user().id());
        assertEquals(user.getId().toString(), jwtDecoder.decode(login.accessToken()).getSubject());
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        authService.register("Test User", "test@example.com", "password-123");

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> authService.register("Another User", "TEST@example.com", "password-456")
        );
    }
}
