package com.example.giga_test.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {}
