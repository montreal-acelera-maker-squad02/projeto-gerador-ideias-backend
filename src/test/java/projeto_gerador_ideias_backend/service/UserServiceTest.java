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
import projeto_gerador_ideias_backend.model.RefreshToken;
import projeto_gerador_ideias_backend.repository.UserRepository;
import projeto_gerador_ideias_backend.service.TokenBlacklistService;

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
    
    @Mock
    private projeto_gerador_ideias_backend.repository.RefreshTokenRepository refreshTokenRepository;
    
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    
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
        when(jwtService.generateAccessToken(anyString(), anyLong())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString(), anyLong())).thenReturn("refresh-token");
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        RegisterResponse response = userService.registerUser(validRequest);
        
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(validRequest.getName(), response.getName());
        assertEquals(validRequest.getEmail(), response.getEmail());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        
        verify(userRepository, times(1)).existsByEmail(validRequest.getEmail());
        verify(passwordEncoder, times(1)).encode(validRequest.getPassword());
        verify(userRepository, times(1)).save(any(User.class));
        verify(jwtService, times(1)).generateAccessToken(anyString(), anyLong());
        verify(jwtService, times(1)).generateRefreshToken(anyString(), anyLong());
        verify(refreshTokenRepository, times(1)).save(any());
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
        
        user.setEnabled(true);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtService.generateAccessToken(user.getEmail(), user.getId())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user.getEmail(), user.getId())).thenReturn("refresh-token");
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        LoginResponse response = userService.login(request);
        
        assertNotNull(response);
        assertEquals(user.getUuid(), response.getUuid());
        assertEquals(user.getName(), response.getName());
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        
        verify(userRepository, times(1)).findByEmail(request.getEmail());
        verify(passwordEncoder, times(1)).matches(request.getPassword(), user.getPassword());
        verify(jwtService, times(1)).generateAccessToken(user.getEmail(), user.getId());
        verify(jwtService, times(1)).generateRefreshToken(user.getEmail(), user.getId());
        verify(refreshTokenRepository, times(1)).save(any());
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
        verify(jwtService, never()).generateAccessToken(anyString(), anyLong());
        verify(jwtService, never()).generateRefreshToken(anyString(), anyLong());
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
        verify(jwtService, never()).generateAccessToken(anyString(), anyLong());
        verify(jwtService, never()).generateRefreshToken(anyString(), anyLong());
    }
    
    @Test
    void shouldThrowExceptionWhenAccountDisabledOnLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        
        User user = new User();
        user.setId(1L);
        user.setEmail("joao@example.com");
        user.setPassword("encodedPassword");
        user.setEnabled(false);
        
        when(userRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.login(request)
        );
        
        assertEquals("Conta desativada. Entre em contato com o suporte.", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenAccountEnabledIsNullOnLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        
        User user = new User();
        user.setId(1L);
        user.setEmail("joao@example.com");
        user.setPassword("encodedPassword");
        user.setEnabled(null);
        
        when(userRepository.findByEmail(request.getEmail())).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.login(request)
        );
        
        assertEquals("Conta desativada. Entre em contato com o suporte.", exception.getMessage());
    }
    
    @Test
    void shouldRefreshTokenSuccessfully() {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");
        
        User user = new User();
        user.setId(1L);
        user.setEmail("joao@example.com");
        user.setEnabled(true);
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("valid-refresh-token");
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(false);
        
        when(jwtService.validateRefreshToken("valid-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-refresh-token"))
            .thenReturn(java.util.Optional.of(refreshToken));
        when(jwtService.generateAccessToken(user.getEmail(), user.getId())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user.getEmail(), user.getId())).thenReturn("new-refresh-token");
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        projeto_gerador_ideias_backend.dto.response.RefreshTokenResponse response = 
            userService.refreshToken(request);
        
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertTrue(refreshToken.getRevoked());
        verify(refreshTokenRepository, times(2)).save(any());
    }
    
    @Test
    void shouldThrowExceptionWhenRefreshTokenInvalid() {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("invalid-token");
        
        when(jwtService.validateRefreshToken("invalid-token")).thenReturn(false);
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.refreshToken(request)
        );
        
        assertEquals("Refresh token inválido ou expirado", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenRefreshTokenNotFound() {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("valid-token");
        
        when(jwtService.validateRefreshToken("valid-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
            .thenReturn(java.util.Optional.empty());
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.refreshToken(request)
        );
        
        assertEquals("Refresh token não encontrado ou revogado", exception.getMessage());
    }
    
    @Test
    void shouldThrowExceptionWhenRefreshTokenExpired() {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("expired-token");
        
        User user = new User();
        user.setId(1L);
        user.setEmail("joao@example.com");
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("expired-token");
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        refreshToken.setRevoked(false);
        
        when(jwtService.validateRefreshToken("expired-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenAndRevokedFalse("expired-token"))
            .thenReturn(java.util.Optional.of(refreshToken));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.refreshToken(request)
        );
        
        assertEquals("Refresh token expirado", exception.getMessage());
        assertTrue(refreshToken.getRevoked());
    }
    
    @Test
    void shouldThrowExceptionWhenAccountDisabledOnRefresh() {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("valid-token");
        
        User user = new User();
        user.setId(1L);
        user.setEmail("joao@example.com");
        user.setEnabled(false);
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("valid-token");
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(false);
        
        when(jwtService.validateRefreshToken("valid-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token"))
            .thenReturn(java.util.Optional.of(refreshToken));
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> userService.refreshToken(request)
        );
        
        assertEquals("Conta desativada. Entre em contato com o suporte.", exception.getMessage());
    }
    
    @Test
    void shouldLogoutWithAccessToken() {
        String accessToken = "access-token-123";
        
        userService.logout(accessToken, null);
        
        verify(tokenBlacklistService, times(1)).blacklistToken(accessToken);
        verify(refreshTokenRepository, never()).findByToken(anyString());
    }
    
    @Test
    void shouldLogoutWithRefreshToken() {
        String refreshToken = "refresh-token-123";
        RefreshToken token = new RefreshToken();
        token.setToken(refreshToken);
        token.setRevoked(false);
        
        when(refreshTokenRepository.findByToken(refreshToken)).thenReturn(java.util.Optional.of(token));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        userService.logout(null, refreshToken);
        
        verify(tokenBlacklistService, never()).blacklistToken(anyString());
        verify(refreshTokenRepository, times(1)).findByToken(refreshToken);
        assertTrue(token.getRevoked());
    }
    
    @Test
    void shouldLogoutWithBothTokens() {
        String accessToken = "access-token-123";
        String refreshToken = "refresh-token-123";
        RefreshToken token = new RefreshToken();
        token.setToken(refreshToken);
        token.setRevoked(false);
        
        when(refreshTokenRepository.findByToken(refreshToken)).thenReturn(java.util.Optional.of(token));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        userService.logout(accessToken, refreshToken);
        
        verify(tokenBlacklistService, times(1)).blacklistToken(accessToken);
        verify(refreshTokenRepository, times(1)).findByToken(refreshToken);
        assertTrue(token.getRevoked());
    }
    
    @Test
    void shouldLogoutWithNullTokens() {
        userService.logout(null, null);
        
        verify(tokenBlacklistService, never()).blacklistToken(anyString());
        verify(refreshTokenRepository, never()).findByToken(anyString());
    }
    
    @Test
    void shouldLogoutWithBlankAccessToken() {
        userService.logout("   ", "refresh-token");
        
        verify(tokenBlacklistService, never()).blacklistToken(anyString());
    }
    
    @Test
    void shouldLogoutWithBlankRefreshToken() {
        userService.logout("access-token", "   ");
        
        verify(refreshTokenRepository, never()).findByToken(anyString());
    }
    
    @Test
    void shouldLogoutWhenRefreshTokenNotFound() {
        String refreshToken = "non-existent-token";
        
        when(refreshTokenRepository.findByToken(refreshToken)).thenReturn(java.util.Optional.empty());
        
        userService.logout(null, refreshToken);
        
        verify(refreshTokenRepository, times(1)).findByToken(refreshToken);
        verify(refreshTokenRepository, never()).save(any());
    }
}


