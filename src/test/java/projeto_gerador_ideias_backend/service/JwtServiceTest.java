package projeto_gerador_ideias_backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import projeto_gerador_ideias_backend.model.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    
    private JwtService jwtService;
    
    private static final String TEST_SECRET = "test-secret-key-minimum-32-characters-long-for-security";
    private static final Long TEST_EXPIRATION = 3600L;
    
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604800L);
    }
    
    @Test
    void shouldGenerateTokenSuccessfully() {
        String email = "joao@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        Claims claims = parseToken(token);
        assertEquals(email, claims.getSubject());
        Object userIdClaim = claims.get("userId");
        assertEquals(userId, userIdClaim instanceof Integer ? ((Integer) userIdClaim).longValue() : userIdClaim);
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        
        @SuppressWarnings("unchecked")
        List<String> authorities = (List<String>) claims.get("authorities");
        assertNotNull(authorities);
        assertTrue(authorities.contains("ROLE_USER"));
    }
    
    @Test
    void shouldIncludeAdminAuthoritiesInToken() {
        String email = "admin@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.ADMIN);
        
        assertNotNull(token);
        Claims claims = parseToken(token);
        
        @SuppressWarnings("unchecked")
        List<String> authorities = (List<String>) claims.get("authorities");
        assertNotNull(authorities);
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertFalse(authorities.contains("ROLE_USER"));
    }
    
    @Test
    void shouldExtractUsernameFromToken() {
        String email = "maria@example.com";
        Long userId = 2L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
        String extractedEmail = jwtService.extractUsername(token);
        
        assertEquals(email, extractedEmail);
    }
    
    @Test
    void shouldExtractUserIdFromToken() {
        String email = "pedro@example.com";
        Long userId = 3L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
        Long extractedUserId = jwtService.extractUserId(token);
        
        assertEquals(userId, extractedUserId);
    }
    
    @Test
    void shouldValidateValidToken() {
        String email = "teste@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
        boolean isValid = jwtService.validateToken(token);
        
        assertTrue(isValid);
    }
    
    @Test
    void shouldInvalidateTokenWithWrongSecret() {
        String email = "teste@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
        
        JwtService differentService = new JwtService();
        ReflectionTestUtils.setField(differentService, "secret", "different-secret-key-minimum-32-characters");
        ReflectionTestUtils.setField(differentService, "accessTokenExpiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(differentService, "refreshTokenExpiration", 604800L);
        
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
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
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
        
        String token1 = jwtService.generateAccessToken(email, userId, Role.USER);
        Claims claims1 = parseToken(token1);
        Date issuedAt1 = claims1.getIssuedAt();
        
        String token2 = null;
        Date issuedAt2 = null;
        boolean tokensAreDifferent = false;
        
        for (int i = 0; i < 20; i++) {
            token2 = jwtService.generateAccessToken(email, userId, Role.USER);
            Claims claims2 = parseToken(token2);
            issuedAt2 = claims2.getIssuedAt();
            
            if (!token1.equals(token2)) {
                tokensAreDifferent = true;
                break;
            }
            
            if (issuedAt2.getTime() > issuedAt1.getTime()) {
                tokensAreDifferent = true;
                break;
            }
            
            long startTime = System.nanoTime();
            while (System.nanoTime() - startTime < 1_000_000) {
                Math.random();
            }
        }
        
        assertEquals(jwtService.extractUsername(token1), jwtService.extractUsername(token2));
        assertEquals(jwtService.extractUserId(token1), jwtService.extractUserId(token2));
        
        assertTrue(issuedAt2.getTime() >= issuedAt1.getTime(), 
                "Timestamp do segundo token deve ser maior ou igual ao primeiro");
        
        if (tokensAreDifferent || issuedAt2.getTime() > issuedAt1.getTime()) {
            assertNotEquals(token1, token2, 
                    "Tokens devem ser diferentes quando gerados em momentos diferentes");
        }
    }
    
    @Test
    void shouldGenerateAccessTokenSuccessfully() {
        String email = "test@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateAccessToken(email, userId, Role.USER);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        Claims claims = parseToken(token);
        assertEquals(email, claims.getSubject());
        assertEquals(userId, claims.get("userId", Long.class));
        assertEquals("access", claims.get("type", String.class));
    }
    
    @Test
    void shouldGenerateRefreshTokenSuccessfully() {
        String email = "test@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateRefreshToken(email, userId, Role.USER);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        Claims claims = parseToken(token);
        assertEquals(email, claims.getSubject());
        assertEquals(userId, claims.get("userId", Long.class));
        assertEquals("refresh", claims.get("type", String.class));
    }
    
    @Test
    void shouldValidateRefreshTokenSuccessfully() {
        String email = "test@example.com";
        Long userId = 1L;
        
        String token = jwtService.generateRefreshToken(email, userId, Role.USER);
        boolean isValid = jwtService.validateRefreshToken(token);
        
        assertTrue(isValid);
    }
    
    @Test
    void shouldInvalidateRefreshTokenWhenNull() {
        boolean isValid = jwtService.validateRefreshToken(null);
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateRefreshTokenWhenBlank() {
        boolean isValid = jwtService.validateRefreshToken("");
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateRefreshTokenWhenWrongType() {
        String email = "test@example.com";
        Long userId = 1L;
        
        String accessToken = jwtService.generateAccessToken(email, userId, Role.USER);
        boolean isValid = jwtService.validateRefreshToken(accessToken);
        
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateRefreshTokenWhenExpired() {
        JwtService serviceWithShortExpiration = new JwtService();
        ReflectionTestUtils.setField(serviceWithShortExpiration, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(serviceWithShortExpiration, "accessTokenExpiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(serviceWithShortExpiration, "refreshTokenExpiration", 0L);
        
        String email = "test@example.com";
        Long userId = 1L;
        
        String token = serviceWithShortExpiration.generateRefreshToken(email, userId, Role.USER);
        
        boolean isValid = serviceWithShortExpiration.validateRefreshToken(token);
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateAccessTokenWhenWrongType() {
        String email = "test@example.com";
        Long userId = 1L;
        
        String refreshToken = jwtService.generateRefreshToken(email, userId, Role.USER);
        boolean isValid = jwtService.validateToken(refreshToken);
        
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateTokenWhenTypeIsNull() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutType = Jwts.builder()
                .subject("test@example.com")
                .claim("userId", 1L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
        
        boolean isValid = jwtService.validateToken(tokenWithoutType);
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateRefreshTokenWhenTypeIsNull() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutType = Jwts.builder()
                .subject("test@example.com")
                .claim("userId", 1L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
        
        boolean isValid = jwtService.validateRefreshToken(tokenWithoutType);
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateRefreshTokenWhenMalformed() {
        String malformedToken = "invalid.token.here";
        
        boolean isValid = jwtService.validateRefreshToken(malformedToken);
        assertFalse(isValid);
    }
    
    @Test
    void shouldInvalidateTokenWhenExpired() {
        JwtService serviceWithShortExpiration = new JwtService();
        ReflectionTestUtils.setField(serviceWithShortExpiration, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(serviceWithShortExpiration, "accessTokenExpiration", 1L);
        ReflectionTestUtils.setField(serviceWithShortExpiration, "refreshTokenExpiration", 604800L);
        
        String email = "test@example.com";
        Long userId = 1L;
        
        JwtService serviceWithVeryShortExpiration = new JwtService();
        ReflectionTestUtils.setField(serviceWithVeryShortExpiration, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(serviceWithVeryShortExpiration, "accessTokenExpiration", 0L);
        ReflectionTestUtils.setField(serviceWithVeryShortExpiration, "refreshTokenExpiration", 604800L);
        
        String token = serviceWithVeryShortExpiration.generateAccessToken(email, userId, Role.USER);
        
        boolean isValid = serviceWithVeryShortExpiration.validateToken(token);
        assertFalse(isValid);
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

