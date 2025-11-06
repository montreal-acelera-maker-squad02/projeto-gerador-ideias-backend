package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;
import projeto_gerador_ideias_backend.service.IdeaService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdeaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private IdeaService ideaService;

    private final String testUserEmail = "controller-user@example.com";
    private User testUser;
    private IdeaResponse mockIdeaResponse;
    private Idea mockIdea;

    @BeforeEach
    @Transactional
    void setUpDatabase() {
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setEmail(testUserEmail);
        testUser.setName("Controller User");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setFavoriteIdeas(new HashSet<>());
        userRepository.save(testUser);

        mockIdea = new Idea(Theme.TECNOLOGIA, "Contexto", "Ideia de Teste", "mistral", 100L);
        mockIdea.setId(1L);
        mockIdea.setUser(testUser);
        mockIdea.setCreatedAt(LocalDateTime.now());

        mockIdeaResponse = new IdeaResponse();
        mockIdeaResponse.setId(1L);
        mockIdeaResponse.setContent("Crie pequenos projetos todos os dias.");
        mockIdeaResponse.setTheme("estudos");
        mockIdeaResponse.setUserName("Controller User");
        mockIdeaResponse.setCreatedAt(LocalDateTime.now());
        mockIdeaResponse.setModelUsed("mistral-mock");
        mockIdeaResponse.setExecutionTimeMs(10L);
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGenerateIdeaSuccessfully() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.ESTUDOS);
        request.setContext("Como aprender Spring Boot");

        when(ideaService.generateIdea(any(IdeaRequest.class), eq(false)))
                .thenReturn(mockIdeaResponse);

        mockMvc.perform(post("/api/ideas/generate")
                        .param("skipCache", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Crie pequenos projetos todos os dias.")))
                .andExpect(jsonPath("$.theme", is("estudos")))
                .andExpect(jsonPath("$.userName", is("Controller User")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnRejectionFromModerationWhenDangerous() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TRABALHO);
        request.setContext("Tópico perigoso");

        IdeaResponse rejectionResponse = new IdeaResponse();
        rejectionResponse.setContent("Desculpe, não posso gerar ideias sobre esse tema.");
        rejectionResponse.setUserName("Controller User");

        when(ideaService.generateIdea(any(IdeaRequest.class), eq(false)))
                .thenReturn(rejectionResponse);

        mockMvc.perform(post("/api/ideas/generate")
                        .param("skipCache", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Desculpe, não posso gerar ideias sobre esse tema.")))
                .andExpect(jsonPath("$.userName", is("Controller User")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);
        request.setContext(null);

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context", is("O contexto não pode estar em branco")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGenerateSurpriseIdeaSuccessfully() throws Exception {
        mockIdeaResponse.setContent("Surpresa: um app de meditação");
        when(ideaService.generateSurpriseIdea()).thenReturn(mockIdeaResponse);

        mockMvc.perform(post("/api/ideas/surprise-me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName", is("Controller User")))
                .andExpect(jsonPath("$.content", is("Surpresa: um app de meditação")));
    }

    @Test
    @WithMockUser
    void shouldListIdeasWithoutFilter() throws Exception {
        List<IdeaResponse> mockList = List.of(mockIdeaResponse, mockIdeaResponse);
        when(ideaService.listarHistoricoIdeiasFiltrado(null, null, null)).thenReturn(mockList);

        mockMvc.perform(get("/api/ideas/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldListIdeasWithThemeFilter() throws Exception {
        List<IdeaResponse> mockList = List.of(mockIdeaResponse);
        when(ideaService.listarHistoricoIdeiasFiltrado(eq("ESTUDOS"), isNull(), isNull()))
                .thenReturn(mockList);

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "ESTUDOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].theme", is("estudos")));
    }

    @Test
    @WithMockUser
    void shouldReturnNotFoundWhenNoIdeasFound() throws Exception {
        when(ideaService.listarHistoricoIdeiasFiltrado(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "TECNOLOGIA"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Nenhuma ideia encontrada")));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenNoIdeasFoundForInvalidDates() throws Exception {
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setName("user5");
        user.setEmail("user5@test.com");
        user.setPassword("123456");
        userRepository.save(user);

        String dataInicioFora = "2099-01-01T00:00:00";
        String dataFimFora = "2099-01-31T23:59:59";

        mockMvc.perform(get("/api/ideas/history")
                        .param("startDate", dataInicioFora)
                        .param("endDate", dataFimFora))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnNotFoundWhenNoMyIdeasFound() throws Exception {
        when(ideaService.listarMinhasIdeias())
                .thenThrow(new ResourceNotFoundException("Nenhuma ideia encontrada para o usuário"));

        mockMvc.perform(get("/api/ideas/my-ideas"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Nenhuma ideia encontrada para o usuário")));
    }

    @Test
    @WithMockUser
    void shouldReturnBadRequestWhenInvalidDateFormat() throws Exception {
        mockMvc.perform(get("/api/ideas/history")
                        .param("startDate", "invalid-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Requisição Inválida")))
                .andExpect(jsonPath("$.message", containsString("não pôde ser convertido para o tipo 'LocalDateTime'")));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldFavoritarIdeiaSuccessfully() throws Exception {
        doNothing().when(ideaService).favoritarIdeia(1L);

        mockMvc.perform(post("/api/ideas/1/favorite"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ideia favoritada com sucesso."));

        verify(ideaService, times(1)).favoritarIdeia(1L);
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenFavoritingNonExistentIdea() throws Exception {
        doThrow(new IllegalArgumentException("Ideia não encontrada."))
                .when(ideaService).favoritarIdeia(99999L);

        mockMvc.perform(post("/api/ideas/99999/favorite"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Ideia não encontrada.")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn400WhenFavoritingAlreadyFavoritedIdea() throws Exception {
        doThrow(new IllegalArgumentException("Ideia já está favoritada."))
                .when(ideaService).favoritarIdeia(1L);

        mockMvc.perform(post("/api/ideas/1/favorite"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ideia já está favoritada."));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldDesfavoritarIdeiaSuccessfully() throws Exception {
        doNothing().when(ideaService).desfavoritarIdeia(1L);

        mockMvc.perform(delete("/api/ideas/1/favorite"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ideia removida dos favoritos com sucesso."));

        verify(ideaService, times(1)).desfavoritarIdeia(1L);
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenDesfavoritingNonExistentIdea() throws Exception {
        doThrow(new IllegalArgumentException("Ideia não encontrada."))
                .when(ideaService).desfavoritarIdeia(99999L);

        mockMvc.perform(delete("/api/ideas/99999/favorite"))
                .andExpect(status().isNotFound());
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenDesfavoritingWithInvalidId() throws Exception {
        long invalidId = Long.MAX_VALUE;
        doThrow(new IllegalArgumentException("Ideia não encontrada."))
                .when(ideaService).desfavoritarIdeia(invalidId);

        mockMvc.perform(delete("/api/ideas/" + invalidId + "/favorite"))
                .andExpect(status().isNotFound());
    }

}
