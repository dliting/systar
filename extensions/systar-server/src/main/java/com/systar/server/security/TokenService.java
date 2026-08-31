package com.systar.server.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class TokenService {

    private final String secret;
    private final int expirationMinutes;

    public TokenService(String secret, int expirationMinutes) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(Long userId, String username, String permissions) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMinutes * 60L * 1000L);

        return Jwts.builder()
                .setSubject(username)
                .claim("user_id", userId)
                .claim("permissions", permissions)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }
}
