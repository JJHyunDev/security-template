package com.example.oauth.domain.security.jwt;

public record JwtTokenClaims(
        Long userId,
        String email,
        String role,
        String provider
) {
}
