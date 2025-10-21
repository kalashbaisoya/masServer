package com.mas.masServer.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final String secret;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public List<String> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);

        Object authoritiesObj = claims.get("authorities");
        if (authoritiesObj instanceof List<?>) {
            List<?> rawList = (List<?>) authoritiesObj;
            List<String> authorities = new ArrayList<>();
            for (Object obj : rawList) {
                if (obj instanceof String) {
                    authorities.add((String) obj);
                } else if (obj instanceof java.util.Map<?, ?> map && map.containsKey("authority")) {
                    // In case Spring serialized authorities as JSON objects like {"authority":"ROLE_USER"}
                    authorities.add(map.get("authority").toString());
                }
            }
            return authorities;
        }

        return new ArrayList<>(); // empty list if missing or invalid type
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
}
