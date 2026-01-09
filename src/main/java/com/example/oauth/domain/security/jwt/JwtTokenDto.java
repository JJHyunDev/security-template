package com.example.oauth.domain.security.jwt;

public record JwtTokenDto(
        String grantType, // Bearer
        String accessToken,
        String refreshToken,
        Long accessTokenExpiration
) {
}
