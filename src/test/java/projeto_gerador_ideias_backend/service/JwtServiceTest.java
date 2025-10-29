package projeto_gerador_ideias_backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    
    private JwtService jwtService;
    
    private static final String TEST_SECRET = "test-secret-key-minimum-32-characters-long-for-security";
    private static final Long TEST_EXPIRATION = 3600L;
    
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", TEST_EXPIRATION);
    }
    
    @Test
    void shouldGenerateTokenSuccessfully() {
        String email = "joao@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateToken(email, userId);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        Claims claims = parseToken(token);
        assertEquals(email, claims.getSubject());
        Object userIdClaim = claims.get("userId");
        assertEquals(userId, userIdClaim instanceof Integer ? ((Integer) userIdClaim).longValue() : userIdClaim);
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }
    
    @Test
    void shouldExtractUsernameFromToken() {
        String email = "maria@example.com";
        Long userId = 2L;
        
        String token = jwtService.generateToken(email, userId);
        String extractedEmail = jwtService.extractUsername(token);
        
        assertEquals(email, extractedEmail);
    }
    
    @Test
    void shouldExtractUserIdFromToken() {
        String email = "pedro@example.com";
        Long userId = 3L;
        
        String token = jwtService.generateToken(email, userId);
        Long extractedUserId = jwtService.extractUserId(token);
        
        assertEquals(userId, extractedUserId);
    }
    
    @Test
    void shouldValidateValidToken() {
        String email = "teste@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateToken(email, userId);
        boolean isValid = jwtService.validateToken(token);
        
        assertTrue(isValid);
    }
    
    @Test
    void shouldInvalidateTokenWithWrongSecret() {
        String email = "teste@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateToken(email, userId);
        
        JwtService differentService = new JwtService();
        ReflectionTestUtils.setField(differentService, "secret", "different-secret-key-minimum-32-characters");
        ReflectionTestUtils.setField(differentService, "expiration", TEST_EXPIRATION);
        
        boolean isValid = differentService.validateToken(token);
        
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateMalformedToken() {
        String malformedToken = "invalid.token.here";
        
        boolean isValid = jwtService.validateToken(malformedToken);
        
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateEmptyToken() {
        boolean isValid = jwtService.validateToken("");
        
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateNullToken() {
        boolean isValid = jwtService.validateToken(null);
        assertFalse(isValid);
    }
    
    @Test
    void shouldGenerateTokenWithCorrectExpiration() {
        String email = "teste@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateToken(email, userId);
        Claims claims = parseToken(token);
        
        Date expiration = claims.getExpiration();
        Date now = new Date();
        long expirationSeconds = (expiration.getTime() - now.getTime()) / 1000;
        
        assertTrue(Math.abs(expirationSeconds - TEST_EXPIRATION) < 5);
    }
    
    @Test
    void shouldExtractUsernameThrowExceptionForInvalidToken() {
        String invalidToken = "invalid.token.here";
        
        assertThrows(Exception.class, () -> {
            jwtService.extractUsername(invalidToken);
        });
    }
    
    @Test
    void shouldExtractUserIdThrowExceptionForInvalidToken() {
        String invalidToken = "invalid.token.here";
        
        assertThrows(Exception.class, () -> {
            jwtService.extractUserId(invalidToken);
        });
    }
    
    @Test
    void shouldGenerateDifferentTokensForSameUser() {
        String email = "teste@example.com";
        Long userId = 1L;
        
        String token1 = jwtService.generateToken(email, userId);
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        String token2 = jwtService.generateToken(email, userId);
        
        assertNotEquals(token1, token2);
        
        assertEquals(jwtService.extractUsername(token1), jwtService.extractUsername(token2));
        assertEquals(jwtService.extractUserId(token1), jwtService.extractUserId(token2));
    }
    
    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

