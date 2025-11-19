package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.request.IdeaRequest;
import projeto_gerador_ideias_backend.dto.response.IdeaResponse;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.ThemeRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;
import projeto_gerador_ideias_backend.config.EmbeddedRedisConfig;
import projeto_gerador_ideias_backend.service.IdeaService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class IdeaControllerTest {

    @Autowired
    private ThemeRepository themeRepository;

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
    private Theme tecnologiaTheme;
    private Theme estudosTheme;
    private Theme trabalhoTheme;

    @BeforeEach
    @Transactional
    void setUpDatabase() {
        ideaRepository.deleteAll();
        userRepository.deleteAll();
        themeRepository.deleteAll();

        if (userRepository.findByEmail(testUserEmail).isEmpty()) {
            testUser = new User();
            testUser.setEmail(testUserEmail);
            testUser.setName("Controller User");
            testUser.setPassword(passwordEncoder.encode("password"));
            testUser.setFavoriteIdeas(new HashSet<>());
            testUser = userRepository.save(testUser);
        } else {
            testUser = userRepository.findByEmail(testUserEmail).orElseThrow();
        }

        tecnologiaTheme = themeRepository.save(new Theme("TECNOLOGIA"));
        estudosTheme = themeRepository.save(new Theme("ESTUDOS"));
        trabalhoTheme = themeRepository.save(new Theme("TRABALHO"));

        mockIdea = new Idea(tecnologiaTheme, "Contexto", "Ideia de Teste", "mistral", 100L);
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
        request.setTheme(estudosTheme.getId());
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
        request.setTheme(trabalhoTheme.getId());
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
        request.setTheme(tecnologiaTheme.getId());
        request.setContext(null);

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("O contexto não pode estar em branco")));

        verify(ideaService, never()).generateIdea(any(), anyBoolean());
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
        Page<IdeaResponse> mockPage = new PageImpl<>(List.of(mockIdeaResponse, mockIdeaResponse));
        when(ideaService.listarHistoricoIdeiasFiltrado(isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/ideas/history")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldListIdeasWithThemeFilter() throws Exception {
        Page<IdeaResponse> mockPage = new PageImpl<>(List.of(mockIdeaResponse));
        when(ideaService.listarHistoricoIdeiasFiltrado(isNull(), eq(3L), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "3")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].theme", is("estudos")));
    }



    @Test
    @WithMockUser
    void shouldReturnNotFoundWhenNoIdeasFound() throws Exception {
        when(ideaService.listarHistoricoIdeiasFiltrado(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Nenhuma ideia encontrada"));

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "1")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Recurso não encontrado")))
                .andExpect(jsonPath("$.message", is("Nenhuma ideia encontrada")));
    }


    @Test
    @WithMockUser
    void shouldReturn404WhenNoIdeasFoundForValidDates() throws Exception {
        LocalDateTime start = LocalDateTime.parse("2099-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2099-01-31T23:59:59");

        when(ideaService.listarHistoricoIdeiasFiltrado(isNull(), isNull(), eq(start), eq(end), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Nenhuma ideia encontrada no banco de dados para os filtros informados."));

        mockMvc.perform(get("/api/ideas/history")
                        .param("startDate", "2099-01-01T00:00:00")
                        .param("endDate", "2099-01-31T23:59:59")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Recurso não encontrado")))
                .andExpect(jsonPath("$.message", containsString("Nenhuma ideia encontrada")));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnNotFoundWhenNoMyIdeasFound() throws Exception {
        reset(ideaService);
        String expectedMessage = "Nenhuma ideia encontrada para o usuário";

        when(ideaService.listarMinhasIdeiasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenThrow(new ResourceNotFoundException(expectedMessage));

        mockMvc.perform(get("/api/ideas/my-ideas?page=0&size=6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is(expectedMessage)));
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

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGetMyIdeasSuccessfully() throws Exception {
        reset(ideaService);

        User user = userRepository.findByEmail(testUserEmail).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        Idea idea1 = new Idea(tecnologiaTheme, "Contexto 1", "Ideia 1", "modelo", 100L);
        idea1.setUser(user);
        idea1.setCreatedAt(now.minusSeconds(1));
        ideaRepository.saveAndFlush(idea1);

        Idea idea2 = new Idea(estudosTheme, "Contexto 2", "Ideia 2", "modelo", 150L);
        idea2.setUser(user);
        idea2.setCreatedAt(now);
        ideaRepository.saveAndFlush(idea2);

        Pageable pageable = PageRequest.of(0, 6);
        List<IdeaResponse> ideaResponses = List.of(new IdeaResponse(idea2), new IdeaResponse(idea1));
        Page<IdeaResponse> ideaPage = new PageImpl<>(ideaResponses, pageable, ideaResponses.size());

        when(ideaService.listarMinhasIdeiasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenReturn(ideaPage);

        mockMvc.perform(get("/api/ideas/my-ideas?page=0&size=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].content", is("Ideia 2")))
                .andExpect(jsonPath("$.content[1].content", is("Ideia 1")))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.pageable.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageable.pageSize", is(6)));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGetMyIdeasWithFiltersSuccessfully() throws Exception {
        reset(ideaService);

        Long themeId = 1L;
        String startDateStr = "2024-01-01T00:00:00";
        String endDateStr = "2024-01-31T23:59:59";

        Pageable pageable = PageRequest.of(0, 6);
        Page<IdeaResponse> filteredPage = new PageImpl<>(List.of(new IdeaResponse()), pageable, 1);

        when(ideaService.listarMinhasIdeiasPaginadas(
                eq(themeId),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0),
                eq(6)
        )).thenReturn(filteredPage);

        mockMvc.perform(get("/api/ideas/my-ideas")
                        .param("page", "0")
                        .param("size", "6")
                        .param("theme", themeId.toString())
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenNoIdeasForUser() throws Exception {
        reset(ideaService);
        when(ideaService.listarMinhasIdeiasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenThrow(new IllegalArgumentException("Nenhuma ideia encontrada para o usuário: " + testUserEmail));

        mockMvc.perform(get("/api/ideas/my-ideas?page=0&size=6"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Nenhuma ideia encontrada")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenResourceNotFoundExceptionInGetFavoriteIdeas() throws Exception {
        reset(ideaService);

        String expectedServiceMessage = "Nenhuma ideia favoritada encontrada para este usuário com os filtros aplicados.";

        when(ideaService.listarIdeiasFavoritadasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenThrow(new ResourceNotFoundException(expectedServiceMessage));

        mockMvc.perform(get("/api/ideas/favorites")
                        .param("page", "0")
                        .param("size", "6"))
                .andExpect(status().isNotFound())

                .andExpect(jsonPath("$.message", is(expectedServiceMessage)))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.totalElements").doesNotExist());
    }



    @Test
    @WithMockUser(username = testUserEmail)
    @Transactional
    void shouldReturnBadRequestWhenFavoritingAlreadyFavoritedIdea() throws Exception {
        reset(ideaService);
        doThrow(new IllegalArgumentException("Ideia já está favoritada."))
                .when(ideaService).favoritarIdeia(1L);

        mockMvc.perform(post("/api/ideas/1/favorite"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ideia já está favoritada."));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn500WhenExceptionInFavoritarIdeia() throws Exception {
        reset(ideaService);
        doThrow(new RuntimeException("Erro ao favoritar"))
                .when(ideaService).favoritarIdeia(1L);
        
        mockMvc.perform(post("/api/ideas/1/favorite"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Erro ao favoritar ideia")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnBadRequestWhenDesfavoritingNotFavoritedIdea() throws Exception {
        reset(ideaService);
        doThrow(new IllegalArgumentException("Ideia não está favoritada."))
                .when(ideaService).desfavoritarIdeia(1L);

        mockMvc.perform(delete("/api/ideas/1/favorite"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ideia não está favoritada."));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn500WhenExceptionInDesfavoritarIdeia() throws Exception {
        reset(ideaService);
        doThrow(new RuntimeException("Erro ao desfavoritar"))
                .when(ideaService).desfavoritarIdeia(1L);
        
        mockMvc.perform(delete("/api/ideas/1/favorite"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Erro ao remover ideia dos favoritos")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn500WhenSurpriseIdeaGenerationFails() throws Exception {
        when(ideaService.generateSurpriseIdea())
                .thenThrow(new RuntimeException("Erro ao gerar ideia surpresa"));

        mockMvc.perform(post("/api/ideas/surprise-me"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn500WhenIdeaGenerationFails() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(tecnologiaTheme.getId());
        request.setContext("Contexto válido");
        
        when(ideaService.generateIdea(any(IdeaRequest.class), anyBoolean()))
                .thenThrow(new RuntimeException("Erro ao gerar ideia"));

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void shouldReturnBadRequestWhenIllegalArgumentExceptionInGetAllIdeas() throws Exception {
        reset(ideaService);
        when(ideaService.listarHistoricoIdeiasFiltrado(any(), eq(99L), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("O tema com ID '99' é inválido."));

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Argumento Inválido")))
                .andExpect(jsonPath("$.message", containsString("O tema com ID '99' é inválido.")));
    }


    @Test
    @WithMockUser
    void shouldListIdeasWithDateFilters() throws Exception {
        reset(ideaService);
        User user = new User();
        user.setName("user3");
        user.setEmail("user3@test.com");
        user.setPassword(passwordEncoder.encode("123456"));
        user.setFavoriteIdeas(new HashSet<>());
        user = userRepository.save(user);

        Idea idea = new Idea(estudosTheme, "Contexto", "Ideia", "modelo", 100L);
        idea.setUser(user);
        idea = ideaRepository.save(idea);

        when(ideaService.listarHistoricoIdeiasFiltrado(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new IdeaResponse(idea))));

        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);

        mockMvc.perform(get("/api/ideas/history")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }


    @Test
    @WithMockUser
    void shouldListIdeasWithThemeAndDateFilters() throws Exception {
        reset(ideaService);

        User user = new User();
        user.setName("user4");
        user.setEmail("user4@test.com");
        user.setPassword(passwordEncoder.encode("123456"));
        user.setFavoriteIdeas(new HashSet<>());
        user = userRepository.save(user);

        Idea idea = new Idea(tecnologiaTheme, "Contexto", "Ideia", "modelo", 100L);
        idea.setUser(user);
        idea.setCreatedAt(LocalDateTime.now());
        idea = ideaRepository.save(idea);

        when(ideaService.listarHistoricoIdeiasFiltrado(
                any(), eq(tecnologiaTheme.getId()), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new IdeaResponse(idea))));

        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", String.valueOf(tecnologiaTheme.getId()))
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].theme", is("TECNOLOGIA")))
                .andExpect(jsonPath("$.content[0].content", is("Ideia")))
                .andExpect(jsonPath("$.content[0].userName", is("user4")));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGenerateIdeaWithSkipCacheTrue() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(estudosTheme.getId());
        request.setContext("Como aprender Spring Boot");

        when(ideaService.generateIdea(any(IdeaRequest.class), eq(true)))
                .thenReturn(mockIdeaResponse);

        mockMvc.perform(post("/api/ideas/generate")
                        .param("skipCache", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Crie pequenos projetos todos os dias.")));

        verify(ideaService, times(1)).generateIdea(any(IdeaRequest.class), eq(true));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn500WhenExceptionInGetMyIdeas() throws Exception {
        reset(ideaService);


        when(ideaService.listarMinhasIdeiasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenThrow(new RuntimeException("Erro inesperado"));

        mockMvc.perform(get("/api/ideas/my-ideas?page=0&size=6"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Erro interno do servidor"))
                .andExpect(jsonPath("$.message").value("Erro inesperado"));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGetFavoriteIdeasWithAllFiltersSuccessfully() throws Exception {
        reset(ideaService);

        Long themeId = 2L;
        String startDate = "2023-10-01T00:00:00";
        String endDate = "2023-10-31T23:59:59";

        Pageable pageable = PageRequest.of(0, 6);
        Page<IdeaResponse> filteredPage = new PageImpl<>(List.of(mockIdeaResponse), pageable, 1);

        when(ideaService.listarIdeiasFavoritadasPaginadas(
                eq(themeId),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0),
                eq(6)
        )).thenReturn(filteredPage);

        mockMvc.perform(get("/api/ideas/favorites")
                        .param("page", "0")
                        .param("size", "6")
                        .param("theme", themeId.toString())
                        .param("startDate", startDate)
                        .param("endDate", endDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGetFavoriteIdeasSuccessfully() throws Exception {
        reset(ideaService);

        Pageable pageable = PageRequest.of(0, 6);
        List<IdeaResponse> favoritasList = List.of(mockIdeaResponse);
        Page<IdeaResponse> favoritasPage = new PageImpl<>(favoritasList, pageable, favoritasList.size());

        when(ideaService.listarIdeiasFavoritadasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenReturn(favoritasPage);

        mockMvc.perform(get("/api/ideas/favorites")
                        .param("page", "0")
                        .param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content", is("Crie pequenos projetos todos os dias.")))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.size", is(6)))
                .andExpect(jsonPath("$.number", is(0)));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn500WhenExceptionInGetFavoriteIdeas() throws Exception {
        reset(ideaService);

        when(ideaService.listarIdeiasFavoritadasPaginadas(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(6)
        )).thenThrow(new RuntimeException("Erro inesperado"));

        mockMvc.perform(get("/api/ideas/favorites")
                        .param("page", "0")
                        .param("size", "6"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").value("Erro inesperado"));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGetStatsSuccessfully() throws Exception {
        when(ideaService.getAverageIdeaGenerationTime()).thenReturn(1550.75);

        mockMvc.perform(get("/api/ideas/generation-stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageGenerationTimeMs", is(1550.75)));

        verify(ideaService, times(1)).getAverageIdeaGenerationTime();
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGetStatsWithNullAverageTime() throws Exception {
        when(ideaService.getAverageIdeaGenerationTime()).thenReturn(null);

        mockMvc.perform(get("/api/ideas/generation-stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageGenerationTimeMs", is(0.0)));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnZeroWhenServiceReturnsZero() throws Exception {
        when(ideaService.getAverageIdeaGenerationTime()).thenReturn(0.0);

        mockMvc.perform(get("/api/ideas/generation-stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageGenerationTimeMs", is(0.0)));

        verify(ideaService, times(1)).getAverageIdeaGenerationTime();
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldHandleLargeDoubleValue() throws Exception {
        double largeValue = 987654321.12345;
        when(ideaService.getAverageIdeaGenerationTime()).thenReturn(largeValue);

        mockMvc.perform(get("/api/ideas/generation-stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageGenerationTimeMs", is(largeValue)));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldHandleSmallFractionalValue() throws Exception {
        double smallValue = 0.000123;
        when(ideaService.getAverageIdeaGenerationTime()).thenReturn(smallValue);

        mockMvc.perform(get("/api/ideas/generation-stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageGenerationTimeMs", is(smallValue)));
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/ideas/generation-stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(ideaService, never()).getAverageIdeaGenerationTime();
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnCorrectContentType() throws Exception {
        mockMvc.perform(get("/api/ideas/generation-stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnOkEvenIfServiceThrowsException() throws Exception {
        when(ideaService.getAverageIdeaGenerationTime()).thenThrow(new RuntimeException("Erro de banco de dados"));

        mockMvc.perform(get("/api/ideas/generation-stats"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Erro interno do servidor")))
                .andExpect(jsonPath("$.message", is("Erro de banco de dados")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    @DisplayName("Deve retornar a contagem de ideias favoritas com sucesso")
    void shouldGetFavoriteIdeasCountSuccessfully() throws Exception {
        // Arrange
        long favoriteCount = 5L;
        when(ideaService.getFavoriteIdeasCount()).thenReturn(favoriteCount);

        // Act & Assert
        mockMvc.perform(get("/api/ideas/favorites/count")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(5)));
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden ao buscar contagem de favoritos sem autenticação")
    void shouldReturnForbiddenWhenGettingFavoritesCountWithoutUser() throws Exception {
        mockMvc.perform(get("/api/ideas/favorites/count"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden ao gerar ideia sem autenticação")
    void shouldReturnForbiddenWhenGeneratingIdeaWithoutUser() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(estudosTheme.getId());
        request.setContext("Contexto");

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden ao gerar ideia surpresa sem autenticação")
    void shouldReturnForbiddenWhenGeneratingSurpriseIdeaWithoutUser() throws Exception {
        mockMvc.perform(post("/api/ideas/surprise-me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden ao listar histórico sem autenticação")
    void shouldReturnForbiddenWhenListingHistoryWithoutUser() throws Exception {
        mockMvc.perform(get("/api/ideas/history"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden ao favoritar ideia sem autenticação")
    void shouldReturnForbiddenWhenFavoritingWithoutUser() throws Exception {
        mockMvc.perform(post("/api/ideas/1/favorite"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden ao desfavoritar ideia sem autenticação")
    void shouldReturnForbiddenWhenUnfavoritingWithoutUser() throws Exception {
        mockMvc.perform(delete("/api/ideas/1/favorite"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    @DisplayName("Deve listar ideias com filtro de usuário")
    void shouldListIdeasWithUserFilter() throws Exception {
        Page<IdeaResponse> mockPage = new PageImpl<>(List.of(mockIdeaResponse));
        when(ideaService.listarHistoricoIdeiasFiltrado(eq(testUser.getId()), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/ideas/history")
                        .param("userId", String.valueOf(testUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userName", is("Controller User")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    @DisplayName("Deve listar ideias com todos os filtros combinados")
    void shouldListIdeasWithAllFilters() throws Exception {
        Page<IdeaResponse> mockPage = new PageImpl<>(List.of(mockIdeaResponse));
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now();

        when(ideaService.listarHistoricoIdeiasFiltrado(eq(testUser.getId()), eq(estudosTheme.getId()), eq(startDate), eq(endDate), anyInt(), anyInt()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/ideas/history")
                        .param("userId", String.valueOf(testUser.getId()))
                        .param("theme", String.valueOf(estudosTheme.getId()))
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].theme", is("estudos")));
    }

    @Test
    @WithMockUser(username = testUserEmail)
    @DisplayName("Deve retornar 500 em generation-stats quando o serviço falha")
    void shouldReturn500WhenStatsServiceFails() throws Exception {
        when(ideaService.getAverageIdeaGenerationTime()).thenThrow(new RuntimeException("Falha no banco de dados"));

        mockMvc.perform(get("/api/ideas/generation-stats"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Erro interno do servidor")))
                .andExpect(jsonPath("$.message", is("Falha no banco de dados")));
    }

    @Test
    @DisplayName("Deve retornar 403 Forbidden em generation-stats sem autenticação")
    void shouldReturnForbiddenForStatsWithoutUser() throws Exception {
        mockMvc.perform(get("/api/ideas/generation-stats"))
                .andExpect(status().isForbidden());
    }
}
