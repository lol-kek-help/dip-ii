package com.example.giga_test;

import com.example.giga_test.security.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void generatedTokenShouldContainSubjectRoleAndExpirationConfig() {
        JwtService jwtService = new JwtService(SECRET, 600);

        String token = jwtService.generateToken("operator1", "OPERATOR");
        var claims = jwtService.parse(token);

        assertNotNull(token);
        assertEquals("operator1", claims.getSubject());
        assertEquals("OPERATOR", claims.get("role", String.class));
        assertEquals(600, jwtService.getExpirationSec());
    }

    @Test
    void parseShouldRejectInvalidToken() {
        JwtService jwtService = new JwtService(SECRET, 600);

        assertThrows(Exception.class, () -> jwtService.parse("invalid.token.value"));
    }
}
