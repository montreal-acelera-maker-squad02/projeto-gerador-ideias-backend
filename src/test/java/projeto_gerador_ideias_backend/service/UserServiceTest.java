package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import projeto_gerador_ideias_backend.dto.request.LoginRequest;
import projeto_gerador_ideias_backend.dto.response.LoginResponse;
import projeto_gerador_ideias_backend.dto.request.RegisterRequest;
import projeto_gerador_ideias_backend.dto.response.RegisterResponse;
import projeto_gerador_ideias_backend.dto.request.UpdateUserRequest;
import projeto_gerador_ideias_backend.exceptions.EmailAlreadyExistsException;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.exceptions.WrongPasswordException;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtService jwtService;
    
    @Mock
    private UserCacheService userCacheService;
    
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
    }
    
    @Test
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName(validRequest.getName());
        savedUser.setEmail(validRequest.getEmail());
        savedUser.setPassword("encodedPassword");
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        RegisterResponse response = userService.registerUser(validRequest);
        
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
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        
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
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setConfirmPassword("Senha@456");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        
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
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@1"); 
        request.setConfirmPassword("Senha@1");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.registerUser(request)
        );
        
        assertTrue(exception.getMessage().contains("8 caracteres"));
        
        verify(userRepository, times(1)).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
    
    @ParameterizedTest
    @MethodSource("invalidPasswordProvider")
    void shouldThrowExceptionWhenPasswordIsInvalid(String email, String password, String confirmPassword) {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        assertThrows(ValidationException.class, () -> userService.registerUser(request));
        
        verify(userRepository, times(1)).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
    
    private static java.util.stream.Stream<Arguments> invalidPasswordProvider() {
        return java.util.stream.Stream.of(
            Arguments.of("joao2@example.com", "senha@123", "senha@123"), // falta maiúscula
            Arguments.of("joao3@example.com", "SENHA@123", "SENHA@123"), // falta minúscula
            Arguments.of("joao4@example.com", "Senha@Boa", "Senha@Boa"), // falta dígito
            Arguments.of("joao5@example.com", "Senha123", "Senha123")   // falta caractere especial
        );
    }
    
    @Test
    void shouldUpdateUserNameSuccessfully() {
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
        
        RegisterResponse response = userService.updateUser(existingUser.getUuid().toString(), request);
        
        assertNotNull(response);
        assertEquals("João Silva Santos", existingUser.getName());
        verify(userRepository, times(1)).save(existingUser);
    }
    
    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.empty());
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Novo Nome");
        
        String uuid = java.util.UUID.randomUUID().toString();
        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(uuid, request));
    }
    
    @Test
    void shouldThrowExceptionWhenOldPasswordMissing() {
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setPassword("encodedPassword");
        existingUser.setUuid(java.util.UUID.randomUUID());
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Nome Novo");
        request.setPassword("NovaSenha@123");
        request.setConfirmPassword("NovaSenha@123");
        
        String uuid = existingUser.getUuid().toString();
        ValidationException exception = assertThrows(ValidationException.class, () ->
            userService.updateUser(uuid, request)
        );
        
        assertEquals("A senha atual é obrigatória para alterar sua senha", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenOldPasswordIncorrect() {
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
        
        String uuid = existingUser.getUuid().toString();
        assertThrows(WrongPasswordException.class, () ->
            userService.updateUser(uuid, request)
        );
    }
    
    @Test
    void shouldThrowExceptionWhenNewPasswordsDoNotMatch() {
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
        
        String uuid = existingUser.getUuid().toString();
        ValidationException exception = assertThrows(ValidationException.class, () ->
            userService.updateUser(uuid, request)
        );
        
        assertEquals("As novas senhas não coincidem", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenNewPasswordInvalid() {
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setPassword("encodedPassword");
        existingUser.setUuid(java.util.UUID.randomUUID());
        
        when(userRepository.findByUuid(any(java.util.UUID.class))).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Nome Novo");
        request.setOldPassword("Senha@123");
        request.setPassword("senha123"); 
        request.setConfirmPassword("senha123");
        
        String uuid = existingUser.getUuid().toString();
        ValidationException exception = assertThrows(ValidationException.class, () ->
            userService.updateUser(uuid, request)
        );
        
        assertTrue(exception.getMessage().contains("8 caracteres"));
    }
    
    @Test
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        
        User user = new User();
        user.setId(1L);
        user.setUuid(java.util.UUID.randomUUID());
        user.setName("João Silva");
        user.setEmail("joao@example.com");
        user.setPassword("encodedPassword");
        
        when(userRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(user.getEmail(), user.getId())).thenReturn("jwt-token-123");
        
        LoginResponse response = userService.login(request);
        
        assertNotNull(response);
        assertEquals(user.getUuid(), response.getUuid());
        assertEquals(user.getName(), response.getName());
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals("jwt-token-123", response.getToken());
        
        verify(userRepository, times(1)).findByEmail(request.getEmail());
        verify(passwordEncoder, times(1)).matches(request.getPassword(), user.getPassword());
        verify(jwtService, times(1)).generateToken(user.getEmail(), user.getId());
    }
    
    @Test
    void shouldThrowExceptionWhenEmailNotFoundOnLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("naoexiste@example.com");
        request.setPassword("Senha@123");
        
        when(userRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.empty());
        
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> userService.login(request)
        );
        
        assertEquals("Credenciais inválidas", exception.getMessage());
        
        verify(userRepository, times(1)).findByEmail(request.getEmail());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(anyString(), anyLong());
    }
    
    @Test
    void shouldThrowExceptionWhenPasswordIncorrectOnLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        request.setPassword("SenhaErrada");
        
        User user = new User();
        user.setId(1L);
        user.setEmail("joao@example.com");
        user.setPassword("encodedPassword");
        
        when(userRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(false);
        
        WrongPasswordException exception = assertThrows(
            WrongPasswordException.class,
            () -> userService.login(request)
        );
        
        assertEquals("Credenciais inválidas", exception.getMessage());
        
        verify(userRepository, times(1)).findByEmail(request.getEmail());
        verify(passwordEncoder, times(1)).matches(request.getPassword(), user.getPassword());
        verify(jwtService, never()).generateToken(anyString(), anyLong());
    }
}


