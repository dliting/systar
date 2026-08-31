package com.systar.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.List;

/**
 * Registers the {@link SystarAuthenticationFilter} and binds security
 * configuration from {@code systar.security.*} properties.
 */
@Configuration
public class SecurityFilterConfig {

    @Bean
    @ConfigurationProperties(prefix = "systar.security")
    public SystarSecurityProperties systarSecurityProperties() {
        return new SystarSecurityProperties();
    }

    @Bean
    public FilterRegistrationBean<SystarAuthenticationFilter> systarAuthFilter(
            SystarSecurityProperties props) {
        List<String> whitelist = parseWhitelist(props.getWhitelist());
        SystarAuthenticationFilter filter = new SystarAuthenticationFilter(
                props.getSecret(), whitelist);

        FilterRegistrationBean<SystarAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        // WebSocket auth is handled by JwtHandshakeInterceptor; servlet filter covers API only.
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    private List<String> parseWhitelist(String whitelistCsv) {
        if (whitelistCsv == null || whitelistCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(whitelistCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Configuration properties for the Systar internal authentication.
     */
    public static class SystarSecurityProperties {
        private String tokenHeader = "X-Systar-Token";
        private String secret = System.getenv().getOrDefault("JWT_SECRET", "changeme-default-secret");
        private String whitelist = "/actuator/health,/error";

        public String getTokenHeader() { return tokenHeader; }
        public void setTokenHeader(String tokenHeader) { this.tokenHeader = tokenHeader; }

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }

        public String getWhitelist() { return whitelist; }
        public void setWhitelist(String whitelist) { this.whitelist = whitelist; }
    }
}
