package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdeaControllerTest {

    public static MockWebServer mockWebServer;

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

    @Autowired
    private projeto_gerador_ideias_backend.service.IdeaService ideaService;

    private final String testUserEmail = "controller-user@example.com";

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @BeforeEach
    void setUpDatabase() {
        userRepository.findAll().forEach(user -> {
            if (user.getFavoriteIdeas() != null && !user.getFavoriteIdeas().isEmpty()) {
                user.getFavoriteIdeas().clear();
                userRepository.save(user);
            }
        });
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        User testUser = new User();
        testUser.setEmail(testUserEmail);
        testUser.setName("Controller User");
        testUser.setPassword(passwordEncoder.encode("password"));
        userRepository.save(testUser);
    }

    @AfterEach
    void tearDownDatabase() {

        ideaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("ollama.base-url", () -> mockWebServer.url("/").toString());
    }

    private String createMockOllamaResponse(String content) throws Exception {
        OllamaResponse ollamaResponse = new OllamaResponse();
        OllamaResponse.Message message = new OllamaResponse.Message();
        message.setRole("assistant");
        message.setContent(content);
        ollamaResponse.setMessage(message);
        return objectMapper.writeValueAsString(ollamaResponse);
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGenerateIdeaSuccessfully() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.ESTUDOS);
        request.setContext("Como aprender Spring Boot");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Crie pequenos projetos todos os dias."))
                .addHeader("Content-Type", "application/json"));

        int requestCountBefore = mockWebServer.getRequestCount();

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Crie pequenos projetos todos os dias.")))
                .andExpect(jsonPath("$.theme", is("estudos")))
                .andExpect(jsonPath("$.userName", is("Controller User")));

        int requestCountAfter = mockWebServer.getRequestCount();
        assertEquals(2, requestCountAfter - requestCountBefore, "Deveria ter feito 2 requisições (1 moderação, 1 geração)");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnRejectionFromModerationWhenDangerous() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TRABALHO);
        request.setContext("Tópico perigoso");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("PERIGOSO"))
                .addHeader("Content-Type", "application/json"));


        int requestCountBefore = mockWebServer.getRequestCount();

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Desculpe, não posso gerar ideias sobre esse tema.")))
                .andExpect(jsonPath("$.userName", is("Controller User")));

        int requestCountAfter = mockWebServer.getRequestCount();
        assertEquals(1, requestCountAfter - requestCountBefore, "Deveria ter feito apenas 1 requisição (moderação)");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnInternalServerErrorWhenModerationFails() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TRABALHO);
        request.setContext("Tópico normal");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("...carregando..."))
                .addHeader("Content-Type", "application/json"));


        int requestCountBefore = mockWebServer.getRequestCount();

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Erro interno do servidor")))
                .andExpect(jsonPath("$.message", is("Falha na moderação: A IA retornou uma resposta inesperada. Tente novamente em alguns segundos.")));

        int requestCountAfter = mockWebServer.getRequestCount();
        assertEquals(1, requestCountAfter - requestCountBefore, "Deveria ter feito apenas 1 requisição (moderação)");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);

        int requestCountBefore = mockWebServer.getRequestCount();

        mockMvc.perform(post("/api/ideas/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context", is("O contexto não pode estar em branco")));

        int requestCountAfter = mockWebServer.getRequestCount();
        assertEquals(0, requestCountAfter - requestCountBefore, "Não deveria fazer requisições se a validação falhar");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldGenerateSurpriseIdeaSuccessfully() throws Exception {
        String mockAiResponse = "A IA gerou esta ideia aleatória.";

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(mockAiResponse))
                .addHeader("Content-Type", "application/json"));

        int requestCountBefore = mockWebServer.getRequestCount();

        mockMvc.perform(post("/api/ideas/surprise-me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName", is("Controller User")))
                .andExpect(jsonPath("$.content", endsWith(mockAiResponse)))
                .andExpect(jsonPath("$.content", anyOf(startsWith("um "), startsWith("uma "))));

        int requestCountAfter = mockWebServer.getRequestCount();
        assertEquals(1, requestCountAfter - requestCountBefore, "Deveria ter feito apenas 1 requisição (sem moderação)");
    }

    @Test
    @WithMockUser
    void shouldListIdeasWithoutFilter() throws Exception {
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setName("user1");
        user.setEmail("user1@test.com");
        user.setPassword("123456");
        userRepository.save(user);

        Idea idea1 = new Idea(Theme.ESTUDOS, "Contexto 1", "Ideia 1", "modeloA", 100L);
        idea1.setUser(user);
        Idea idea2 = new Idea(Theme.TRABALHO, "Contexto 2", "Ideia 2", "modeloB", 150L);
        idea2.setUser(user);
        ideaRepository.saveAll(List.of(idea1, idea2));

        mockMvc.perform(get("/api/ideas/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldListIdeasWithThemeFilter() throws Exception {
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setName("user2");
        user.setEmail("user2@test.com");
        user.setPassword("123456");
        userRepository.save(user);

        Idea idea = new Idea(Theme.ESTUDOS, "Contexto Filtro", "Ideia Filtrada", "modeloX", 200L);
        idea.setUser(user);
        ideaRepository.save(idea);

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "ESTUDOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].theme", is("estudos")));
    }

    @Test
    @WithMockUser
    void shouldReturnNotFoundWhenNoIdeasFound() throws Exception {
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc.perform(get("/api/ideas/history")
                        .param("theme", "TECNOLOGIA"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Nenhuma ideia encontrada no banco de dados para os filtros informados."));
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

        Idea idea = new Idea(Theme.ESTUDOS, "Contexto", "Ideia", "modelo", 100L);
        idea.setUser(user);
        ideaRepository.save(idea);

        mockMvc.perform(get("/api/ideas/history")
                        .param("startDate", "2024-01-01T00:00:00")
                        .param("endDate", "2023-01-01T00:00:00"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldReturn500WhenInvalidDateFormat() throws Exception {
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc.perform(get("/api/ideas/history")
                        .param("startDate", "invalid-date"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Erro interno")));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    @Transactional
    void shouldFavoritarIdeiaSuccessfully() throws Exception {
        User user = userRepository.findByEmail(testUserEmail).orElseThrow();
        user.getFavoriteIdeas().clear();
        userRepository.saveAndFlush(user);

        Idea idea = new Idea(Theme.TECNOLOGIA, "Contexto", "Ideia", "modelo", 100L);
        idea.setUser(user);
        idea = ideaRepository.save(idea);

        mockMvc.perform(post("/api/ideas/" + idea.getId() + "/favorite"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ideia favoritada com sucesso."));
        User updatedUser = userRepository.findByEmail(testUserEmail).orElseThrow();
        assertEquals(1, updatedUser.getFavoriteIdeas().size());
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenFavoritingNonExistentIdea() throws Exception {
        mockMvc.perform(post("/api/ideas/99999/favorite"))
                .andExpect(status().isNotFound());
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenFavoritingWithInvalidId() throws Exception {
        mockMvc.perform(post("/api/ideas/" + Long.MAX_VALUE + "/favorite"))
                .andExpect(status().isNotFound());
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldDesfavoritarIdeiaSuccessfully() throws Exception {
        User user = userRepository.findByEmail(testUserEmail).orElseThrow();

        Idea idea = new Idea(Theme.TECNOLOGIA, "Contexto", "Ideia", "modelo", 100L);
        idea.setUser(user);
        idea = ideaRepository.save(idea);


        mockMvc.perform(post("/api/ideas/" + idea.getId() + "/favorite"))
                .andExpect(status().isOk());


        mockMvc.perform(delete("/api/ideas/" + idea.getId() + "/favorite"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ideia removida dos favoritos com sucesso."));
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenDesfavoritingNonExistentIdea() throws Exception {
        mockMvc.perform(delete("/api/ideas/99999/favorite"))
                .andExpect(status().isNotFound());
    }


    @Test
    @WithMockUser(username = testUserEmail)
    void shouldReturn404WhenDesfavoritingWithInvalidId() throws Exception {
        mockMvc.perform(delete("/api/ideas/" + Long.MAX_VALUE + "/favorite"))
                .andExpect(status().isNotFound());
    }

}
