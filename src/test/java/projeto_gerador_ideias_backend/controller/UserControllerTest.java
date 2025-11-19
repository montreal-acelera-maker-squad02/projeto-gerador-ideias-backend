package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import projeto_gerador_ideias_backend.config.EmbeddedRedisConfig;
import projeto_gerador_ideias_backend.dto.request.UpdateUserRequest;
import projeto_gerador_ideias_backend.dto.response.RegisterResponse;
import projeto_gerador_ideias_backend.dto.response.UserStatsResponse;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.service.UserService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private UserService userService;
    
    @Test
    @WithMockUser(username = "joao@example.com")
    void shouldUpdateUserSuccessfully() throws Exception {
        String uuid = UUID.randomUUID().toString();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("João Silva Santos");
        
        RegisterResponse response = new RegisterResponse();
        response.setId(1L);
        response.setUuid(UUID.fromString(uuid));
        response.setName("João Silva Santos");
        response.setEmail("joao@example.com");
        response.setCreatedAt(LocalDateTime.now());
        
        when(userService.updateUser(uuid, request)).thenReturn(response);
        
        mockMvc.perform(put("/api/users/{uuid}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("João Silva Santos"));
        
        verify(userService, times(1)).updateUser(uuid, request);
    }
    
    @Test
    @WithMockUser(username = "user@example.com")
    void shouldReturnNotFoundWhenUserNotExists() throws Exception {
        String uuid = UUID.randomUUID().toString();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Novo Nome");
        
        when(userService.updateUser(anyString(), any(UpdateUserRequest.class)))
                .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));
        
        mockMvc.perform(put("/api/users/{uuid}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso não encontrado"));
        
        verify(userService, times(1)).updateUser(anyString(), any(UpdateUserRequest.class));
    }

    @Test
    @WithMockUser(username = "test-user@example.com")
    void shouldGetUserStatsSuccessfully() throws Exception {
        // Arrange
        UserStatsResponse statsResponse = new UserStatsResponse(123L);
        when(userService.getUserStats()).thenReturn(statsResponse);

        // Act & Assert
        mockMvc.perform(get("/api/users/me/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedIdeasCount", is(123)));
    }

    @Test
    void shouldReturnForbiddenWhenGettingStatsWithoutUser() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/users/me/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                // Spring Security 6+ retorna 403 (Forbidden) por padrão para endpoints seguros sem autenticação,
                // em vez de 401, pois a ausência de token é tratada como falha de autorização.
                .andExpect(status().isForbidden());
    }
}
