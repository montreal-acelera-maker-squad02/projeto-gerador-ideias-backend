package projeto_gerador_ideias_backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    
    private static final String CLAIM_USER_ID = "userId";
    
    @Value("${jwt.secret:}")
    private String secret;
    
    @Value("${jwt.access-token-expiration:600}")
    private Long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800}")
    private Long refreshTokenExpiration;
    
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret não configurado! " +
                "Configure a variável de ambiente JWT_SECRET ou crie um arquivo .env na raiz do projeto " +
                "com JWT_SECRET= sua-chave-segura (veja .env.example)"
            );
        }
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public String generateAccessToken(String email, Long userId) {
        Date now = new Date();
        Date expiryDate = Date.from(Instant.now().plus(accessTokenExpiration, ChronoUnit.SECONDS));
        
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public String generateRefreshToken(String email, Long userId) {
        Date now = new Date();
        Date expiryDate = Date.from(Instant.now().plus(refreshTokenExpiration, ChronoUnit.SECONDS));
        
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * @deprecated Use {@link #generateAccessToken(String, Long)} instead.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public String generateToken(String email, Long userId) {
        return generateAccessToken(email, userId);
    }
    
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }
    
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }
    
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);
            if (type == null || !"access".equals(type)) {
                return false;
            }
            return claims.getExpiration().after(new Date());
        } catch (ExpiredJwtException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);
            if (type == null || !"refresh".equals(type)) {
                return false;
            }
            return claims.getExpiration().after(new Date());
        } catch (ExpiredJwtException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}


