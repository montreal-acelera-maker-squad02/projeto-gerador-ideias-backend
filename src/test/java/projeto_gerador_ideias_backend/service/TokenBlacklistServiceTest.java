package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenBlacklistServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getCache("tokenBlacklist")).thenReturn(cache);
    }

    @Test
    void shouldBlacklistTokenSuccessfully() {
        String token = "test-token-123";
        
        tokenBlacklistService.blacklistToken(token);
        
        verify(cache, times(1)).put(token, true);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldNotBlacklistInvalidToken(String token) {
        tokenBlacklistService.blacklistToken(token);
        
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void shouldReturnTrueWhenTokenIsBlacklisted() {
        String token = "blacklisted-token";
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(true);
        when(cache.get(token)).thenReturn(wrapper);
        
        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(token);
        
        assertTrue(isBlacklisted);
        verify(cache, times(1)).get(token);
    }

    @Test
    void shouldReturnFalseWhenTokenIsNotBlacklisted() {
        String token = "valid-token";
        when(cache.get(token)).thenReturn(null);
        
        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(token);
        
        assertFalse(isBlacklisted);
        verify(cache, times(1)).get(token);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldReturnFalseWhenTokenIsInvalid(String token) {
        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(token);
        
        assertFalse(isBlacklisted);
        verify(cache, never()).get(anyString());
    }

    @Test
    void shouldReturnFalseWhenWrapperValueIsNull() {
        String token = "token-with-null-value";
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(null);
        when(cache.get(token)).thenReturn(wrapper);
        
        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(token);
        
        assertFalse(isBlacklisted);
        verify(cache, times(1)).get(token);
    }

    @Test
    void shouldRemoveTokenFromBlacklistSuccessfully() {
        String token = "token-to-remove";
        
        tokenBlacklistService.removeFromBlacklist(token);
        
        verify(cache, times(1)).evict(token);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldNotRemoveInvalidTokenFromBlacklist(String token) {
        tokenBlacklistService.removeFromBlacklist(token);
        
        verify(cache, never()).evict(anyString());
    }

    @Test
    void shouldThrowExceptionWhenCacheNotFound() {
        when(cacheManager.getCache("tokenBlacklist")).thenReturn(null);
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> tokenBlacklistService.blacklistToken("test-token")
        );
        
        assertTrue(exception.getMessage().contains("Cache 'tokenBlacklist' não encontrado"));
    }
}

