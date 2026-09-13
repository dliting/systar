package com.systar.server.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ops whitelist references {@code /actuator/health}, but no actuator
 * dependency existed: the request fell through to the static-resource handler
 * and Spring 6.1's {@code NoResourceFoundException} surfaced through the
 * catch-all exception handler as a generic 500 — a defect that turned every
 * unknown path (for callers past the auth filter) into a 500 instead of a 404.
 * Pins both ends: the whitelisted health probe exists, and unknown API paths
 * answer a proper 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("GET /actuator/health serves the ops probe; unknown paths answer 404")
class ActuatorHealthTest {

    /** Self-minted probe token lifetime; the filter checks the signature, not the exp slack. */
    private static final long TOKEN_TTL_MS = 60000L;

    @Autowired
    private MockMvc mockMvc;

    /** Same secret the auth filter validates against (systar.security.secret). */
    @Value("${systar.security.secret}")
    private String secret;

    @Test
    @DisplayName("GET /actuator/health answers 200 UP for the whitelisted ops probe")
    void healthEndpointServesOpsProbe() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("an unknown authenticated API path answers a 404 Result, not the generic 500")
    void unknownPathAnswers404() throws Exception {
        mockMvc.perform(get("/api/monitor/nonexistent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /**
     * The auth filter guards {@code /api/*} and rejects unauthenticated requests
     * with 401 before any MVC mapping — an unknown path is only reachable with a
     * valid JWT, so the 404 contract must be pinned authenticated.
     */
    private String validToken() {
        return Jwts.builder()
                .setSubject("actuator-health-test")
                .claim("user_id", 1L)
                .claim("permissions", "*")
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_TTL_MS))
                .signWith(SignatureAlgorithm.HS512, secret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }
}
