package projeto_gerador_ideias_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private CustomUserDetailsService userDetailsService;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUuid(UUID.randomUUID());
        testUser.setName("João Silva");
        testUser.setEmail("joao@example.com");
        testUser.setPassword("encodedPassword123");
    }
    
    @Test
    void shouldLoadUserByUsernameSuccessfully() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(testUser.getEmail());
        
        assertNotNull(userDetails);
        assertEquals(testUser.getEmail(), userDetails.getUsername());
        assertEquals(testUser.getPassword(), userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isCredentialsNonExpired());
        
        verify(userRepository, times(1)).findByEmail(testUser.getEmail());
    }
    
    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserNotFound() {
        String email = "naoexiste@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        
        UsernameNotFoundException exception = assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername(email)
        );
        
        assertTrue(exception.getMessage().contains("Usuário não encontrado"));
        assertTrue(exception.getMessage().contains(email));
        
        verify(userRepository, times(1)).findByEmail(email);
    }
    
    @Test
    void shouldReturnUserWithRoleUser() {
        testUser.setRole(projeto_gerador_ideias_backend.model.Role.USER);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(testUser.getEmail());
        
        assertEquals(1, userDetails.getAuthorities().size());
        assertEquals("ROLE_USER", userDetails.getAuthorities().iterator().next().getAuthority());
    }
    
    @Test
    void shouldReturnUserWithRoleAdmin() {
        testUser.setRole(projeto_gerador_ideias_backend.model.Role.ADMIN);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(testUser.getEmail());
        
        assertEquals(1, userDetails.getAuthorities().size());
        assertEquals("ROLE_ADMIN", userDetails.getAuthorities().iterator().next().getAuthority());
    }
    
    @Test
    void shouldReturnUserWithRoleUserWhenRoleIsNull() {
        testUser.setRole(null);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(testUser.getEmail());
        
        assertEquals(1, userDetails.getAuthorities().size());
        assertEquals("ROLE_USER", userDetails.getAuthorities().iterator().next().getAuthority());
    }
}

