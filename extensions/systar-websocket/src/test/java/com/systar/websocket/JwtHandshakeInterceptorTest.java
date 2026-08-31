package com.systar.websocket;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class JwtHandshakeInterceptorTest {

    private static final String SECRET = "test-secret-key-for-junit";
    private static final String USERNAME = "testUser";
    private static final Long USER_ID = 42L;

    private JwtHandshakeInterceptor interceptor;
    private WebSocketHandler mockHandler;
    private ServerHttpResponse mockResponse;

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor(SECRET);
        mockHandler = mock(WebSocketHandler.class);
        mockResponse = mock(ServerHttpResponse.class);
    }

    private String createValidToken() {
        return Jwts.builder()
                .setSubject(USERNAME)
                .claim("user_id", USER_ID)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(SignatureAlgorithm.HS256, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    private String createExpiredToken() {
        return Jwts.builder()
                .setSubject(USERNAME)
                .claim("user_id", USER_ID)
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(SignatureAlgorithm.HS256, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    private String createWrongSecretToken() {
        return Jwts.builder()
                .setSubject(USERNAME)
                .claim("user_id", USER_ID)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(SignatureAlgorithm.HS256, "wrong-secret".getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    private Map<String, Object> createAttributes() {
        return new HashMap<>();
    }

    // ======================== Valid Token ========================

    @Nested
    @DisplayName("Valid token scenarios")
    class ValidToken {

        @Test
        @DisplayName("valid JWT token sets userId and username in attributes")
        void validTokenSetsAttributes() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", createValidToken());
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isTrue();
            assertThat(attributes).containsEntry(JwtHandshakeInterceptor.ATTR_USER_ID, USER_ID);
            assertThat(attributes).containsEntry(JwtHandshakeInterceptor.ATTR_USERNAME, USERNAME);
        }

        @Test
        @DisplayName("valid JWT returns true for beforeHandshake")
        void validTokenReturnsTrue() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", createValidToken());
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isTrue();
        }
    }

    // ======================== Missing/Blank Token ========================

    @Nested
    @DisplayName("Missing or blank token")
    class MissingToken {

        @Test
        @DisplayName("missing token parameter returns false")
        void missingTokenReturnsFalse() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
            assertThat(attributes).isEmpty();
        }

        @Test
        @DisplayName("blank token parameter returns false")
        void blankTokenReturnsFalse() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", "   ");
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("empty token parameter returns false")
        void emptyTokenReturnsFalse() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", "");
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
        }
    }

    // ======================== Invalid Token ========================

    @Nested
    @DisplayName("Invalid token")
    class InvalidToken {

        @Test
        @DisplayName("expired token returns false")
        void expiredTokenReturnsFalse() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", createExpiredToken());
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("token signed with wrong secret returns false")
        void wrongSecretReturnsFalse() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", createWrongSecretToken());
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("garbage token string returns false")
        void garbageTokenReturnsFalse() {
            MockHttpServletRequest httpReq = new MockHttpServletRequest();
            httpReq.setParameter("token", "not-a-valid-jwt");
            ServerHttpRequest request = new ServletServerHttpRequest(httpReq);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
        }
    }

    // ======================== Non-Servlet Request ========================

    @Nested
    @DisplayName("Non-servlet request")
    class NonServletRequest {

        @Test
        @DisplayName("non-ServletServerHttpRequest returns false")
        void nonServletRequestReturnsFalse() {
            ServerHttpRequest request = mock(ServerHttpRequest.class);
            Map<String, Object> attributes = createAttributes();

            boolean result = interceptor.beforeHandshake(request, mockResponse, mockHandler, attributes);

            assertThat(result).isFalse();
        }
    }

    // ======================== After Handshake ========================

    @Nested
    @DisplayName("afterHandshake")
    class AfterHandshake {

        @Test
        @DisplayName("afterHandshake is a no-op and does not throw")
        void afterHandshakeDoesNotThrow() {
            interceptor.afterHandshake(mock(ServerHttpRequest.class), mock(ServerHttpResponse.class),
                    mockHandler, null);
            // no exception means success
        }
    }
}
