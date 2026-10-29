package com.example.CodeForge.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(UUID id, String username, String email, String firstName, String lastName) {
    }
}
