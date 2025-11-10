package projeto_gerador_ideias_backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Cache<String, User> emailCache;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private UserDetails userDetails;

    private UserCacheService userCacheService;

    @BeforeEach
    void setUp() {
        userCacheService = new UserCacheService(userRepository, emailCache);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        userCacheService.clearRequestCache();
    }

    @Test
    void shouldGetCurrentAuthenticatedUserFromRequestCache() {
        User cachedUser = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(cachedUser);

        User firstCall = userCacheService.getCurrentAuthenticatedUser();
        User secondCall = userCacheService.getCurrentAuthenticatedUser();

        assertSame(firstCall, secondCall);
        verify(userRepository, never()).findByEmail(anyString());
        verify(emailCache, times(1)).getIfPresent(anyString());
    }

    @Test
    void shouldGetCurrentAuthenticatedUserFromEmailCache() {
        User cachedUser = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(cachedUser);

        User user = userCacheService.getCurrentAuthenticatedUser();

        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        verify(userRepository, never()).findByEmail(anyString());
        verify(emailCache, times(1)).getIfPresent("test@example.com");
    }

    @Test
    void shouldGetCurrentAuthenticatedUserFromRepository() {
        User user = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        User result = userCacheService.getCurrentAuthenticatedUser();

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(emailCache, times(1)).put("test@example.com", user);
    }

    @Test
    void shouldThrowExceptionWhenAuthenticationIsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userCacheService.getCurrentAuthenticatedUser();
        });

        assertEquals("Usuário não autenticado. Não é possível usar o chat.", exception.getMessage());
        verify(userRepository, never()).findByEmail(anyString());
        verify(emailCache, never()).getIfPresent(anyString());
    }

    @Test
    void shouldThrowExceptionWhenPrincipalIsNotUserDetails() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("not a UserDetails");

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userCacheService.getCurrentAuthenticatedUser();
        });

        assertEquals("Usuário não autenticado. Não é possível usar o chat.", exception.getMessage());
        verify(userRepository, never()).findByEmail(anyString());
        verify(emailCache, never()).getIfPresent(anyString());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundInRepository() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("notfound@example.com");
        when(emailCache.getIfPresent("notfound@example.com")).thenReturn(null);
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userCacheService.getCurrentAuthenticatedUser();
        });

        assertTrue(exception.getMessage().contains("Usuário autenticado não encontrado no banco de dados"));
        assertTrue(exception.getMessage().contains("notfound@example.com"));
        verify(userRepository, times(1)).findByEmail("notfound@example.com");
    }

    @Test
    void shouldClearRequestCache() {
        User user = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(user);

        userCacheService.getCurrentAuthenticatedUser();
        userCacheService.clearRequestCache();

        when(emailCache.getIfPresent("test@example.com")).thenReturn(user);
        User result = userCacheService.getCurrentAuthenticatedUser();

        assertNotNull(result);
        verify(emailCache, times(2)).getIfPresent("test@example.com");
    }

    @Test
    void shouldInvalidateUserCache() {
        userCacheService.invalidateUserCache("test@example.com");

        verify(emailCache, times(1)).invalidate("test@example.com");
    }

    @Test
    void shouldInvalidateAllCache() {
        userCacheService.invalidateAllCache();

        verify(emailCache, times(1)).invalidateAll();
    }

    @Test
    void shouldPutUserInEmailCacheWhenFetchedFromRepository() {
        User user = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        userCacheService.getCurrentAuthenticatedUser();

        verify(emailCache, times(1)).put("test@example.com", user);
    }

    @Test
    void shouldStoreUserInRequestCacheAfterFetchingFromRepository() {
        User user = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        User firstCall = userCacheService.getCurrentAuthenticatedUser();
        User secondCall = userCacheService.getCurrentAuthenticatedUser();

        assertSame(firstCall, secondCall);
        verify(userRepository, times(1)).findByEmail("test@example.com");
    }

    @Test
    void shouldStoreUserInRequestCacheAfterFetchingFromEmailCache() {
        User cachedUser = createTestUser("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(emailCache.getIfPresent("test@example.com")).thenReturn(cachedUser);

        User firstCall = userCacheService.getCurrentAuthenticatedUser();
        User secondCall = userCacheService.getCurrentAuthenticatedUser();

        assertSame(firstCall, secondCall);
        verify(emailCache, times(1)).getIfPresent("test@example.com");
        verify(userRepository, never()).findByEmail(anyString());
    }

    private User createTestUser(String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setName("Test User");
        return user;
    }
}

