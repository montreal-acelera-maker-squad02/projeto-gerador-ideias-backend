package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import projeto_gerador_ideias_backend.dto.request.LoginRequest;
import projeto_gerador_ideias_backend.dto.response.LoginResponse;
import projeto_gerador_ideias_backend.dto.request.RegisterRequest;
import projeto_gerador_ideias_backend.dto.response.RegisterResponse;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.security.JwtAuthenticationFilter;
import projeto_gerador_ideias_backend.service.JwtService;
import projeto_gerador_ideias_backend.service.UserService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(projeto_gerador_ideias_backend.exceptions.GlobalExceptionHandler.class)
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private JwtService jwtService;
    
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setConfirmPassword("Senha@123");
        
        RegisterResponse response = new RegisterResponse();
        response.setId(1L);
        response.setUuid(UUID.randomUUID());
        response.setName("João Silva");
        response.setEmail("joao@example.com");
        response.setCreatedAt(LocalDateTime.now());
        
        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(response);
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.createdAt").exists());
        
        verify(userService, times(1)).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnConflictWhenEmailExists() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setConfirmPassword("Senha@123");
        
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new projeto_gerador_ideias_backend.exceptions.EmailAlreadyExistsException("Email já está em uso: joao@example.com"));
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cadastro não realizado"))
                .andExpect(jsonPath("$.message").exists());
        
        verify(userService, times(1)).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenValidationFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("senha123");
        request.setConfirmPassword("senha123");
        
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new ValidationException("A senha deve ter no mínimo 8 caracteres..."));
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Erro de validação"))
                .andExpect(jsonPath("$.message").exists());
        
        verify(userService, times(1)).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenNameIsMissing() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("invalid-email");
        request.setPassword("Senha@123");
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senh");
        request.setConfirmPassword("Senh");
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        
        LoginResponse response = new LoginResponse();
        response.setUuid(UUID.randomUUID());
        response.setName("João Silva");
        response.setEmail("joao@example.com");
        response.setAccessToken("jwt-token-123");
        response.setRefreshToken("refresh-token-123");
        
        when(userService.login(any(LoginRequest.class))).thenReturn(response);
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.accessToken").value("jwt-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-123"))
                .andExpect(jsonPath("$.uuid").exists());
        
        verify(userService, times(1)).login(any(LoginRequest.class));
    }
    
    @Test
    void shouldReturnUnauthorizedWhenCredentialsIncorrect() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        request.setPassword("SenhaErrada");
        
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new projeto_gerador_ideias_backend.exceptions.WrongPasswordException("Credenciais inválidas"));
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Erro de autenticação"));
        
        verify(userService, times(1)).login(any(LoginRequest.class));
    }
    
    @Test
    void shouldReturnNotFoundWhenUserNotFoundOnLogin() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("naoexiste@example.com");
        request.setPassword("Senha@123");
        
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException("Credenciais inválidas"));
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso não encontrado"));
        
        verify(userService, times(1)).login(any(LoginRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenLoginRequestIsInvalid() throws Exception {
        LoginRequest request = new LoginRequest();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).login(any(LoginRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenLoginEmailIsInvalid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("invalid-email");
        request.setPassword("Senha@123");
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).login(any(LoginRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenPasswordIsMissing() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("joao@example.com");
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).login(any(LoginRequest.class));
    }
    
    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");
        
        projeto_gerador_ideias_backend.dto.response.RefreshTokenResponse response = 
            new projeto_gerador_ideias_backend.dto.response.RefreshTokenResponse();
        response.setAccessToken("new-access-token");
        response.setRefreshToken("new-refresh-token");
        
        when(userService.refreshToken(any(projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest.class)))
            .thenReturn(response);
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
        
        verify(userService, times(1)).refreshToken(any(projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenRefreshTokenInvalid() throws Exception {
        projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest request = 
            new projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest();
        request.setRefreshToken("invalid-refresh-token");
        
        when(userService.refreshToken(any(projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest.class)))
            .thenThrow(new ValidationException("Refresh token inválido ou expirado"));
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Erro de validação"));
        
        verify(userService, times(1)).refreshToken(any(projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest.class));
    }
    
    @Test
    void shouldLogoutSuccessfullyWithAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer access-token-123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        
        verify(userService, times(1)).logout("access-token-123", null);
    }
    
    @Test
    void shouldLogoutSuccessfullyWithRefreshToken() throws Exception {
        projeto_gerador_ideias_backend.dto.request.LogoutRequest request = 
            new projeto_gerador_ideias_backend.dto.request.LogoutRequest();
        request.setRefreshToken("refresh-token-123");
        
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer access-token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        
        verify(userService, times(1)).logout("access-token-123", "refresh-token-123");
    }
    
    @Test
    void shouldLogoutSuccessfullyWithoutTokens() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        
        verify(userService, times(1)).logout(null, null);
    }
    
    @Test
    void shouldLogoutWithRefreshTokenOnly() throws Exception {
        projeto_gerador_ideias_backend.dto.request.LogoutRequest request = 
            new projeto_gerador_ideias_backend.dto.request.LogoutRequest();
        request.setRefreshToken("refresh-token-123");
        
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        
        verify(userService, times(1)).logout(null, "refresh-token-123");
    }
}


