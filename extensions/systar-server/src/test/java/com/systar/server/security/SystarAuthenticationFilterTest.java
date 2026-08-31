package com.systar.server.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SystarAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests";

    @AfterEach
    void tearDown() { SystarSecurityContext.clear(); }

    @Test
    void shouldPassWhitelistedPathWithoutToken() throws Exception {
        var filter = new SystarAuthenticationFilter(SECRET, List.of("/actuator/health"));
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        var filter = new SystarAuthenticationFilter(SECRET, List.of("/actuator/health"));
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldAcceptValidJwtBearerToken() throws Exception {
        String jwt = Jwts.builder()
                .setSubject("admin")
                .claim("user_id", 1L)
                .claim("permissions", "*")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS512, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of("/actuator/health"));
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        // Context is cleared after filter chain completes (ThreadLocal leak prevention)
        assertNull(SystarSecurityContext.get());
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {
        String jwt = Jwts.builder()
                .setSubject("admin")
                .claim("user_id", 1L)
                .claim("permissions", "*")
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(SignatureAlgorithm.HS512, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldClearSecurityContextAfterRequest() throws Exception {
        String jwt = Jwts.builder()
                .setSubject("admin")
                .claim("user_id", 1L)
                .claim("permissions", "*")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS512, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        // Critical: ThreadLocal must be cleared after request
        assertNull(SystarSecurityContext.get());
    }

    @Test
    void shouldRejectMalformedJwt() throws Exception {
        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer not.a.valid.jwt");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectJwtWithWrongKey() throws Exception {
        String jwt = Jwts.builder()
                .setSubject("admin")
                .claim("user_id", 1L)
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS512, "wrong-key".getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldHandleBlankBearerToken() throws Exception {
        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer ");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectJwtWithMissingUserIdClaim() throws Exception {
        String jwt = Jwts.builder()
                .setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS512, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectJwtWithMissingPermissionsClaim() throws Exception {
        String jwt = Jwts.builder()
                .setSubject("admin")
                .claim("user_id", 1L)
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS512, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectJwtWithMissingSubjectClaim() throws Exception {
        String jwt = Jwts.builder()
                .claim("user_id", 1L)
                .claim("permissions", "*")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(SignatureAlgorithm.HS512, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        var filter = new SystarAuthenticationFilter(SECRET, List.of());
        var request = new MockHttpServletRequest("GET", "/api/monitor/tree");
        request.addHeader("Authorization", "Bearer " + jwt);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

}
