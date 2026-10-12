package com.eduexam.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void tokenCarriesTheAccountTokenVersion() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expirationMinutes", 10);
        jwtUtil.validateConfiguration();

        String token = jwtUtil.generateToken(42L, 7);

        assertTrue(jwtUtil.validateToken(token));
        assertEquals(42L, jwtUtil.getUserIdFromToken(token));
        assertEquals(7, jwtUtil.getTokenVersionFromToken(token));
    }
}
