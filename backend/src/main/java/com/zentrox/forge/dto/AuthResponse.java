package com.zentrox.forge.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, String tokenType) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthResponse(accessToken, refreshToken, expiresInSeconds, "Bearer");
    }
}
