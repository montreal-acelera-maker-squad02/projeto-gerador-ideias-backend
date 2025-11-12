package projeto_gerador_ideias_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import projeto_gerador_ideias_backend.service.JwtService;
import projeto_gerador_ideias_backend.service.TokenBlacklistService;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    
    @Mock
    private JwtService jwtService;
    
    @Mock
    private UserDetailsService userDetailsService;
    
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    private UserDetails testUserDetails;
    
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        testUserDetails = User.builder()
                .username("joao@example.com")
                .password("encodedPassword")
                .authorities(Collections.emptyList())
                .build();
    }
    
    @Test
    void shouldContinueWhenNoAuthorizationHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenAuthorizationHeaderDoesNotStartWithBearer() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Invalid token");
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenTokenIsInvalid() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(tokenBlacklistService.isTokenBlacklisted("invalid-token")).thenReturn(false);
        when(jwtService.validateToken("invalid-token")).thenReturn(false);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).validateToken("invalid-token");
        verify(jwtService, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        String email = "joao@example.com";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(testUserDetails);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).extractUsername(validToken);
        verify(userDetailsService, times(1)).loadUserByUsername(email);
        
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(email, SecurityContextHolder.getContext().getAuthentication().getName());
    }
    
    @Test
    void shouldContinueWhenUserNotFound() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        String email = "naoexiste@example.com";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).extractUsername(validToken);
        verify(userDetailsService, times(1)).loadUserByUsername(email);
        
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldNotSetAuthenticationWhenEmailDoesNotMatch() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        String emailFromToken = "joao@example.com";
        String emailFromUser = "different@example.com";
        
        UserDetails differentUser = User.builder()
                .username(emailFromUser)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(emailFromToken);
        when(userDetailsService.loadUserByUsername(emailFromToken)).thenReturn(differentUser);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldNotProcessWhenAlreadyAuthenticated() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        String email = "joao@example.com";
        
        org.springframework.security.core.Authentication existingAuth = 
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        testUserDetails, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(email);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenTokenIsBlacklisted() throws ServletException, IOException {
        String blacklistedToken = "blacklisted-token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + blacklistedToken);
        when(tokenBlacklistService.isTokenBlacklisted(blacklistedToken)).thenReturn(true);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, never()).validateToken(anyString());
        verify(jwtService, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenExtractUsernameThrowsException() throws ServletException, IOException {
        String validToken = "valid-token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenThrow(new RuntimeException("Token parsing error"));
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).extractUsername(validToken);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenUserIsDisabled() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        String email = "joao@example.com";
        
        UserDetails disabledUser = User.builder()
                .username(email)
                .password("encodedPassword")
                .authorities(Collections.emptyList())
                .disabled(true)
                .build();
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(disabledUser);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).extractUsername(validToken);
        verify(userDetailsService, times(1)).loadUserByUsername(email);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenUserEmailIsNull() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(null);
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).extractUsername(validToken);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    
    @Test
    void shouldContinueWhenLoadUserByUsernameThrowsException() throws ServletException, IOException {
        String validToken = "valid-jwt-token";
        String email = "joao@example.com";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(tokenBlacklistService.isTokenBlacklisted(validToken)).thenReturn(false);
        when(jwtService.validateToken(validToken)).thenReturn(true);
        when(jwtService.extractUsername(validToken)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email))
                .thenThrow(new RuntimeException("Database error"));
        
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtService, times(1)).extractUsername(validToken);
        verify(userDetailsService, times(1)).loadUserByUsername(email);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

