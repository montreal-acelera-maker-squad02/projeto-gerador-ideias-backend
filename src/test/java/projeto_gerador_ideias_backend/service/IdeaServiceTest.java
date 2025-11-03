package projeto_gerador_ideias_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdeaServiceTest {

    public MockWebServer mockWebServer;

    @Mock
    private IdeaRepository ideaRepository;

    @Mock
    private UserRepository userRepository;

    private IdeaService ideaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private final String testUserEmail = "test@example.com";

    @BeforeEach
    void setUpEach() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient.Builder webClientBuilder = WebClient.builder();

        ideaService = new IdeaService(ideaRepository, userRepository, webClientBuilder, baseUrl);
        ReflectionTestUtils.setField(ideaService, "ollamaModel", "mistral-test");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(testUserEmail);
        testUser.setName("Test User");
        testUser.setUuid(UUID.randomUUID());

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(testUserEmail)
                .password("password")
                .roles("USER")
                .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDownEach() throws IOException {
        mockWebServer.shutdown();
        SecurityContextHolder.clearContext();
    }

    private String createMockOllamaResponse(String content) throws JsonProcessingException {
        OllamaResponse ollamaResponse = new OllamaResponse();
        OllamaResponse.Message message = new OllamaResponse.Message();
        message.setRole("assistant");
        message.setContent(content);
        ollamaResponse.setMessage(message);
        return objectMapper.writeValueAsString(ollamaResponse);
    }

    @Test
    void shouldGenerateIdeaWhenModerationIsSafe() throws Exception {
        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));

        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);
        request.setContext("Um app de lista de tarefas");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Ideia gerada: Criar um app focado em gamificação."))
                .addHeader("Content-Type", "application/json"));

        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea idea = invocation.getArgument(0);
            idea.setId(1L);
            return idea;
        });

        IdeaResponse response = ideaService.generateIdea(request);

        assertNotNull(response);
        assertEquals("Ideia gerada: Criar um app focado em gamificação.", response.getContent());
        assertEquals("tecnologia", response.getTheme());
        assertEquals(2, mockWebServer.getRequestCount());

        verify(ideaRepository).save(argThat(idea ->
                idea.getUser().getId().equals(1L) &&
                        idea.getGeneratedContent().equals("Ideia gerada: Criar um app focado em gamificação.")
        ));
    }

    @Test
    void shouldRejectIdeaWhenModerationIsDangerous() throws Exception {
        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));

        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);
        request.setContext("Como fazer phishing");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("PERIGOSO"))
                .addHeader("Content-Type", "application/json"));

        IdeaResponse response = ideaService.generateIdea(request);

        assertNotNull(response);
        assertEquals("Desculpe, não posso gerar ideias sobre esse tema.", response.getContent());
        assertEquals("Test User", response.getUserName());
        assertEquals(1, mockWebServer.getRequestCount());
        verify(ideaRepository, never()).save(any(Idea.class));
    }

    @Test
    void shouldThrowExceptionWhenModerationResponseIsUnclear() throws Exception {
        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));

        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);
        request.setContext("Contexto normal");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Resposta estranha"))
                .addHeader("Content-Type", "application/json"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ideaService.generateIdea(request);
        });

        assertTrue(exception.getMessage().contains("Falha na moderação"));
        assertEquals(1, mockWebServer.getRequestCount());
        verify(ideaRepository, never()).save(any(Idea.class));
    }

    @Test
    void shouldHandleOllamaException() {
        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));

        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);
        request.setContext("Contexto válido");

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ideaService.generateIdea(request);
        });

        assertTrue(exception.getMessage().contains("Erro ao se comunicar com a IA (Ollama)"));
        verify(ideaRepository, never()).save(any(Idea.class));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Idea ideaMock;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        startDate = LocalDateTime.of(2025, 10, 1, 0, 0);
        endDate = LocalDateTime.of(2025, 10, 31, 23, 59);

        ideaMock = new Idea(Theme.TECNOLOGIA, "contexto teste", "ideia gerada", "modelo-teste", 120L);
    }

    @Test
    void deveListarHistorico_ComTemaEData() {
        when(ideaRepository.findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(
                Theme.TECNOLOGIA, startDate, endDate))
                .thenReturn(List.of(ideaMock));

        List<IdeaResponse> result = ideaService.listarHistoricoIdeiasFiltrado("TECNOLOGIA", startDate, endDate);

        assertEquals(1, result.size());
        verify(ideaRepository, times(1))
                .findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(Theme.TECNOLOGIA, startDate, endDate);
    }

    @Test
    void deveListarHistorico_ApenasComTema() {
        when(ideaRepository.findByThemeOrderByCreatedAtDesc(Theme.TECNOLOGIA))
                .thenReturn(List.of(ideaMock));

        List<IdeaResponse> result = ideaService.listarHistoricoIdeiasFiltrado("TECNOLOGIA", null, null);

        assertEquals(1, result.size());
        verify(ideaRepository, times(1))
                .findByThemeOrderByCreatedAtDesc(Theme.TECNOLOGIA);
    }

    @Test
    void deveListarHistorico_ApenasComDatas() {
        when(ideaRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate))
                .thenReturn(List.of(ideaMock));

        List<IdeaResponse> result = ideaService.listarHistoricoIdeiasFiltrado(null, startDate, endDate);

        assertEquals(1, result.size());
        verify(ideaRepository, times(1))
                .findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
    }

    @Test
    void deveListarHistorico_SemFiltros() {
        when(ideaRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(ideaMock));

        List<IdeaResponse> result = ideaService.listarHistoricoIdeiasFiltrado(null, null, null);

        assertEquals(1, result.size());
        verify(ideaRepository, times(1))
                .findAllByOrderByCreatedAtDesc();
    }

    @Test
    void deveListarIdeiasPorUsuario_ComSucesso() {
        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(ideaMock));

        List<IdeaResponse> result = ideaService.listarIdeiasPorUsuario(1L);

        assertEquals(1, result.size());
        verify(ideaRepository, times(1))
                .findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void deveLancarExcecao_QuandoUsuarioSemIdeias() {
        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ideaService.listarIdeiasPorUsuario(1L)
        );

        assertEquals("Nenhuma ideia encontrada para o usuário com ID: 1", ex.getMessage());
        verify(ideaRepository, times(1))
                .findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void shouldGenerateSurpriseIdeaSuccessfully() throws Exception {
        String mockAiResponse = "A IA gerou esta ideia aleatória.";

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(mockAiResponse))
                .addHeader("Content-Type", "application/json"));

        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea idea = invocation.getArgument(0);
            idea.setId(1L);
            return idea;
        });

        IdeaResponse response = ideaService.generateSurpriseIdea();

        assertEquals(1, mockWebServer.getRequestCount());

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertTrue(requestBody.contains("Gere um nome de startup") ||
                requestBody.contains("Gere um slogan de marketing") ||
                requestBody.contains("Gere uma ideia de produto") ||
                requestBody.contains("Gere um post para redes sociais"));

        assertNotNull(response);
        assertEquals("Test User", response.getUserName());

        assertTrue(response.getContent().endsWith(mockAiResponse));
        assertTrue(response.getContent().contains(": "));

        verify(ideaRepository).save(argThat(idea ->
                idea.getUser().getId().equals(1L) &&
                        idea.getGeneratedContent().endsWith(mockAiResponse) &&
                        idea.getGeneratedContent().startsWith("um ")
        ));
    }
}