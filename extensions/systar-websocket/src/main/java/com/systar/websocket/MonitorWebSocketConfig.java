package com.systar.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSocket configuration for monitor result real-time push.
 * <p>
 * Registers {@link MonitorWebSocketHandler} at {@code /ws} endpoint
 * with JWT authentication via {@link JwtHandshakeInterceptor}.
 * Uses {@code setAllowedOriginPatterns} to support wildcard port matching.
 */
@Configuration
@EnableWebSocket
public class MonitorWebSocketConfig implements WebSocketConfigurer {

    private static final String JWT_SECRET =
            System.getenv().getOrDefault("JWT_SECRET", "changeme-default-secret");

    private final MonitorWebSocketHandler monitorWebSocketHandler;

    public MonitorWebSocketConfig(MonitorWebSocketHandler monitorWebSocketHandler) {
        this.monitorWebSocketHandler = monitorWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitorWebSocketHandler, "/ws")
                .addInterceptors(new JwtHandshakeInterceptor(JWT_SECRET))
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
