package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import projeto_gerador_ideias_backend.dto.RegisterResponse;
import projeto_gerador_ideias_backend.dto.UpdateUserRequest;
import projeto_gerador_ideias_backend.security.JwtAuthenticationFilter;
import projeto_gerador_ideias_backend.service.JwtService;
import projeto_gerador_ideias_backend.service.UserService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class, 
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(projeto_gerador_ideias_backend.exceptions.GlobalExceptionHandler.class)
class UserControllerTest {
    
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
    void shouldUpdateUserSuccessfully() throws Exception {
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
        
        mockMvc.perform(put("/api/users/{uuid}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("João Silva Santos"));
        
        verify(userService, times(1)).updateUser(uuid, request);
    }
    
    @Test
    void shouldReturnNotFoundWhenUserNotExists() throws Exception {
        String uuid = java.util.UUID.randomUUID().toString();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Novo Nome");
        
        when(userService.updateUser(anyString(), any(UpdateUserRequest.class)))
                .thenThrow(new projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException("Usuário não encontrado"));
        
        mockMvc.perform(put("/api/users/{uuid}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso não encontrado"));
        
        verify(userService, times(1)).updateUser(anyString(), any(UpdateUserRequest.class));
    }
}

