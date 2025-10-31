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
import org.springframework.test.web.servlet.MockMvc;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
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

    private final String testUserEmail = "controller-user@example.com";

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @BeforeEach
    void setUpDatabase() {
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
}