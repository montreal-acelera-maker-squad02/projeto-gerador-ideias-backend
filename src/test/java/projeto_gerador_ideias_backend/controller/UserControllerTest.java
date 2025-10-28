package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import projeto_gerador_ideias_backend.dto.RegisterRequest;
import projeto_gerador_ideias_backend.dto.RegisterResponse;
import projeto_gerador_ideias_backend.dto.UpdateUserRequest;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.service.UserService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserService userService;
    
    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setConfirmPassword("Senha@123");
        
        RegisterResponse response = new RegisterResponse();
        response.setId(1L);
        response.setUuid(java.util.UUID.randomUUID());
        response.setName("João Silva");
        response.setEmail("joao@example.com");
        response.setCreatedAt(LocalDateTime.now());
        
        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/api/users/register")
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
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setConfirmPassword("Senha@123");
        
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new projeto_gerador_ideias_backend.exceptions.EmailAlreadyExistsException("Email já está em uso: joao@example.com"));
        
        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cadastro não realizado"))
                .andExpect(jsonPath("$.message").exists());
        
        verify(userService, times(1)).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenValidationFails() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("senha123"); // senha inválida
        
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new ValidationException("A senha deve ter no mínimo 8 caracteres..."));
        
        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Erro de validação"))
                .andExpect(jsonPath("$.message").exists());
        
        verify(userService, times(1)).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenNameIsMissing() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        // name está null
        
        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenEmailIsInvalid() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("invalid-email"); // email inválido
        request.setPassword("Senha@123");
        
        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldReturnBadRequestWhenPasswordIsTooShort() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("João Silva");
        request.setEmail("joao@example.com");
        request.setPassword("Senh");
        request.setConfirmPassword("Senh");
        
        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(userService, never()).registerUser(any(RegisterRequest.class));
    }
    
    @Test
    void shouldUpdateUserSuccessfully() throws Exception {
        // Arrange
        String uuid = java.util.UUID.randomUUID().toString();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("João Silva Santos");
        
        RegisterResponse response = new RegisterResponse();
        response.setId(1L);
        response.setUuid(java.util.UUID.fromString(uuid));
        response.setName("João Silva Santos");
        response.setEmail("joao@example.com");
        response.setCreatedAt(LocalDateTime.now());
        
        when(userService.updateUser(uuid, request)).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(put("/api/users/{uuid}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("João Silva Santos"));
        
        verify(userService, times(1)).updateUser(uuid, request);
    }
    
    @Test
    void shouldReturnNotFoundWhenUserNotExists() throws Exception {
        // Arrange
        String uuid = java.util.UUID.randomUUID().toString();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Novo Nome");
        
        when(userService.updateUser(anyString(), any(UpdateUserRequest.class)))
                .thenThrow(new projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException("Usuário não encontrado"));
        
        // Act & Assert
        mockMvc.perform(put("/api/users/{uuid}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso não encontrado"));
        
        verify(userService, times(1)).updateUser(anyString(), any(UpdateUserRequest.class));
    }
}

