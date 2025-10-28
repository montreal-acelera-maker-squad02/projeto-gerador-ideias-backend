package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import projeto_gerador_ideias_backend.dto.RegisterRequest;
import projeto_gerador_ideias_backend.dto.RegisterResponse;
import projeto_gerador_ideias_backend.dto.UpdateUserRequest;
import projeto_gerador_ideias_backend.exceptions.EmailAlreadyExistsException;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.exceptions.WrongPasswordException;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserService userService;
    
    private RegisterRequest validRequest;
    
    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest();
        validRequest.setName("João Silva");
        validRequest.setEmail("joao@example.com");
        validRequest.setPassword("Senha@123");
        validRequest.setConfirmPassword("Senha@123");
        
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
    }
    
    @Test
    void shouldRegisterUserSuccessfully() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName(validRequest.getName());
        savedUser.setEmail(validRequest.getEmail());
        savedUser.setPassword("encodedPassword");
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // Act
        RegisterResponse response = userService.registerUser(validRequest);
        
        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(validRequest.getName(), response.getName());
        assertEquals(validRequest.getEmail(), response.getEmail());
        
        verify(userRepository, times(1)).existsByEmail(validRequest.getEmail());
        verify(passwordEncoder, times(1)).encode(validRequest.getPassword());
        verify(userRepository, times(1)).save(any(User.class));
    }
    
    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        
        // Act & Assert
        EmailAlreadyExistsException exception = assertThrows(
            EmailAlreadyExistsException.class,
            () -> userService.registerUser(validRequest)
        );
        
        assertEquals("Email já está em uso: " + validRequest.getEmail(), exception.getMessage());
        
        verify(userRepository, times(1)).existsByEmail(validRequest.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordsDoNotMatch() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setConfirmPassword("Senha@456"); // diferente
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        // Act & Assert
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.registerUser(request)
        );
        
        assertEquals("As senhas não coincidem", exception.getMessage());
        
        verify(userRepository, times(1)).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordIsTooShort() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@1"); // 7 caracteres
        request.setConfirmPassword("Senha@1");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        // Act & Assert
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.registerUser(request)
        );
        
        assertTrue(exception.getMessage().contains("8 caracteres"));
        
        verify(userRepository, times(1)).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordLacksUppercase() {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao2@example.com");
        request.setPassword("senha@123");
        request.setConfirmPassword("senha@123");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        assertThrows(ValidationException.class, () -> userService.registerUser(request));
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordLacksLowercase() {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao3@example.com");
        request.setPassword("SENHA@123");
        request.setConfirmPassword("SENHA@123");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        assertThrows(ValidationException.class, () -> userService.registerUser(request));
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordLacksDigit() {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao4@example.com");
        request.setPassword("Senha@Boa");
        request.setConfirmPassword("Senha@Boa");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        assertThrows(ValidationException.class, () -> userService.registerUser(request));
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordLacksSpecialChar() {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao5@example.com");
        request.setPassword("Senha123");
        request.setConfirmPassword("Senha123");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        assertThrows(ValidationException.class, () -> userService.registerUser(request));
    }
    
    // === Testes para updateUser ===
    
    @Test
    void shouldUpdateUserNameSuccessfully() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUuid(java.util.UUID.randomUUID());
        existingUser.setName("João Silva");
        existingUser.setEmail("joao@example.com");
        existingUser.setPassword("encodedPassword");
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("João Silva Santos");
        
        // Act
        RegisterResponse response = userService.updateUser(existingUser.getUuid().toString(), request);
        
        // Assert
        assertNotNull(response);
        assertEquals("João Silva Santos", existingUser.getName());
        verify(userRepository, times(1)).save(existingUser);
    }
    
    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        // Arrange
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.empty());
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Novo Nome");
        
        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            userService.updateUser(java.util.UUID.randomUUID().toString(), request)
        );
    }
    
    @Test
    void shouldThrowExceptionWhenOldPasswordMissing() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setPassword("encodedPassword");
        existingUser.setUuid(java.util.UUID.randomUUID());
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Nome Novo");
        request.setPassword("NovaSenha@123");
        request.setConfirmPassword("NovaSenha@123");
        
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            userService.updateUser(existingUser.getUuid().toString(), request)
        );
        
        assertEquals("A senha atual é obrigatória para alterar sua senha", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenOldPasswordIncorrect() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setPassword("encodedPassword");
        existingUser.setUuid(java.util.UUID.randomUUID());
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Nome Novo");
        request.setOldPassword("SenhaErrada");
        request.setPassword("NovaSenha@123");
        request.setConfirmPassword("NovaSenha@123");
        
        // Act & Assert
        assertThrows(WrongPasswordException.class, () ->
            userService.updateUser(existingUser.getUuid().toString(), request)
        );
    }
    
    @Test
    void shouldThrowExceptionWhenNewPasswordsDoNotMatch() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setPassword("encodedPassword");
        existingUser.setUuid(java.util.UUID.randomUUID());
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Nome Novo");
        request.setOldPassword("Senha@123");
        request.setPassword("NovaSenha@123");
        request.setConfirmPassword("SenhaDiferente@456");
        
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            userService.updateUser(existingUser.getUuid().toString(), request)
        );
        
        assertEquals("As novas senhas não coincidem", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenNewPasswordInvalid() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setPassword("encodedPassword");
        existingUser.setUuid(java.util.UUID.randomUUID());
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Nome Novo");
        request.setOldPassword("Senha@123");
        request.setPassword("senha123"); // inválida
        request.setConfirmPassword("senha123");
        
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            userService.updateUser(existingUser.getUuid().toString(), request)
        );
        
        assertTrue(exception.getMessage().contains("8 caracteres"));
    }
}

