package com.systar.websocket;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Validates a JWT token from the {@code token} query parameter during
 * the WebSocket handshake and populates session attributes with user
 * identity from the verified claims.
 * <p>
 * <strong>Token format:</strong> the client passes the JWT as a
 * {@code ?token=<jwt>} query parameter on the WebSocket URL.
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";

    private final String secret;

    public JwtHandshakeInterceptor(String secret) {
        this.secret = secret;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WebSocket handshake rejected: unsupported request type {}", request.getClass());
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: missing token parameter");
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(token)
                    .getBody();

            attributes.put(ATTR_USER_ID, claims.get("user_id", Long.class));
            attributes.put(ATTR_USERNAME, claims.getSubject());
            log.debug("WebSocket authenticated: user={}", claims.getSubject());
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("WebSocket handshake rejected: expired token - {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.warn("WebSocket handshake rejected: invalid signature - {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: invalid token - {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no post-handshake action needed
    }
}
