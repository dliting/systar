package com.systar.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systar.common.api.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SystarAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SystarAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String secret;
    private final List<String> whitelist;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SystarAuthenticationFilter(String secret, List<String> whitelist) {
        this.secret = secret;
        this.whitelist = List.copyOf(whitelist);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        try {
            for (String pattern : whitelist) {
                if (pathMatcher.match(pattern, path)) {
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // 1. JWT Bearer token (primary — from HTTP header)
            String jwt = extractJwtFromHeader(request);
            // 2. Query param fallback (WebSocket can't send custom headers)
            if (jwt == null) {
                jwt = request.getParameter("token");
            }
            if (StringUtils.hasText(jwt)) {
                try {
                    Claims claims = Jwts.parser()
                            .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                            .parseClaimsJws(jwt)
                            .getBody();

                    Long   userId      = claims.get("user_id", Long.class);
                    String username    = claims.getSubject();
                    String permissions = claims.get("permissions", String.class);

                    if (userId == null || username == null || username.isBlank()
                            || permissions == null) {
                        sendUnauthorized(response, "Invalid token: missing required claims");
                        return;
                    }

                    SystarUser user = new SystarUser(userId, username, permissions);
                    SystarSecurityContext.set(user);
                    filterChain.doFilter(request, response);
                    return;
                } catch (JwtException e) {
                    log.debug("JWT parse failed: {}", e.getMessage());
                }
            }

            sendUnauthorized(response, "Missing authentication token");
        } catch (Exception e) {
            log.error("Authentication error", e);
            sendUnauthorized(response, "Authentication failed");
        } finally {
            SystarSecurityContext.clear();
        }
    }

    private static String extractJwtFromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        log.warn("Auth failed: {}", message);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                Result.error(Result.CODE_UNAUTHORIZED, message));
    }
}
