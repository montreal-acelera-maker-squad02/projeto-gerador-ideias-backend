package projeto_gerador_ideias_backend.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import projeto_gerador_ideias_backend.dto.request.IdeaRequest;
import projeto_gerador_ideias_backend.dto.response.OllamaResponse;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.ChatSessionRepository;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.ThemeRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;
import projeto_gerador_ideias_backend.service.IdeaService;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.cache.type=NONE",})
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
class LoggingAspectTest {

    public static MockWebServer mockWebServer;

    @Autowired
    private IdeaService ideaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ThemeRepository themeRepository;

    private final String testUserEmail = "aspect-user@example.com";
    private Theme defaultTheme;
    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @BeforeEach
    void setUpDatabase() {
        chatSessionRepository.deleteAll();
        ideaRepository.deleteAll();
        userRepository.deleteAll();
        themeRepository.deleteAll();

        User testUser = new User();
        testUser.setEmail(testUserEmail);
        testUser.setName("Aspect User");
        testUser.setPassword(passwordEncoder.encode("password"));
        userRepository.save(testUser);

        defaultTheme = new Theme();
        defaultTheme.setName("Estudos");
        defaultTheme = themeRepository.save(defaultTheme);
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
    void shouldLogAroundGenerateIdea(CapturedOutput output) throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(defaultTheme.getId());
        request.setContext("Como aprender AOP");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Crie testes para seus aspectos."))
                .addHeader("Content-Type", "application/json"));

        ideaService.generateIdea(request, false);

        String logs = output.getAll();
        assertThat(logs)
                .contains(">> Iniciando: generateIdea() | Contexto: 'Como aprender AOP'")
                .contains("<< Finalizado com sucesso: generateIdea() | Tempo de Execução Total:")
                .contains("ms");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldLogAroundGenerateSurpriseIdea(CapturedOutput output) throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Uma ideia aleatória incrível."))
                .addHeader("Content-Type", "application/json"));

        ideaService.generateSurpriseIdea();

        String logs = output.getAll();
        assertThat(logs)
                .contains(">> Iniciando: generateSurpriseIdea() | Contexto: 'Surprise Me!'")
                .contains("<< Finalizado com sucesso: generateSurpriseIdea() | Tempo de Execução Total:")
                .contains("ms");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldLogStartButNotEndWhenExceptionIsThrown(CapturedOutput output) {

        IdeaRequest request = new IdeaRequest();
        request.setTheme(defaultTheme.getId());
        request.setContext("Contexto que causa erro");

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(RuntimeException.class, () -> {
            ideaService.generateIdea(request, false);
        });

        String logs = output.getAll();
        assertThat(logs).contains(">> Iniciando: generateIdea() | Contexto: 'Contexto que causa erro'");
        assertThat(logs).contains("<< Finalizado com erro: generateIdea() | Tempo de Execução Total:");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldLogCorrectlyWhenModerationRejects(CapturedOutput output) throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(defaultTheme.getId());
        request.setContext("Um contexto perigoso");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("PERIGOSO"))
                .addHeader("Content-Type", "application/json"));

        assertDoesNotThrow(() -> {
            ideaService.generateIdea(request, false);
        });

        String logs = output.getAll();
        assertThat(logs)
                .contains(">> Iniciando: generateIdea() | Contexto: 'Um contexto perigoso'")
                .contains("<< Finalizado com sucesso: generateIdea() | Tempo de Execução Total:")
                .contains("ms");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldNotLogMethodsThatDoNotMatchPointcut(CapturedOutput output) {
        User user = userRepository.findByEmail(testUserEmail).orElseThrow();
        Idea idea = new Idea(defaultTheme, "contexto", "conteudo", "modelo", 100L);
        idea.setUser(user);
        ideaRepository.save(idea);

        ideaService.listarHistoricoIdeiasFiltrado(null, null, null, null, 0, 10);

        assertThat(output.getAll()).doesNotContain(">> Iniciando: listarHistoricoIdeiasFiltrado()");
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldIncrementCounterAndRecordTimeOnSuccessfulIdeaGeneration() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(defaultTheme.getId());
        request.setContext("Teste de métricas");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Ideia para métricas."))
                .addHeader("Content-Type", "application/json"));

        MetricState initialState = getMetricState("generateIdea", "success");

        ideaService.generateIdea(request, false);

        assertThat(getIdeasGeneratedCount()).isEqualTo(initialState.counterValue() + 1.0);
        assertThat(getTimerCount("generateIdea", "success"))
                .isEqualTo(initialState.timerCount() + 1L);
        assertThat(getTimerTotalTime("generateIdea", "success"))
                .isGreaterThan(initialState.totalTime());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldNotIncrementCounterButRecordTimeOnModerationRejection() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(defaultTheme.getId());
        request.setContext("Contexto perigoso para métricas");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("PERIGOSO"))
                .addHeader("Content-Type", "application/json"));

        MetricState initialState = getMetricState("generateIdea", "success");

        assertDoesNotThrow(() -> {
            ideaService.generateIdea(request, false);
        });

        assertThat(getIdeasGeneratedCount()).isEqualTo(initialState.counterValue());
        assertThat(getTimerCount("generateIdea", "success"))
                .isEqualTo(initialState.timerCount() + 1L);
        assertThat(getTimerTotalTime("generateIdea", "success"))
                .isGreaterThan(initialState.totalTime());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldIncrementCounterAndRecordTimeOnSuccessfulSurpriseIdeaGeneration() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Uma ideia aleatória incrível."))
                .addHeader("Content-Type", "application/json"));

        MetricState initialState = getMetricState("generateSurpriseIdea", "success");

        ideaService.generateSurpriseIdea();

        assertThat(getIdeasGeneratedCount()).isEqualTo(initialState.counterValue() + 1.0);
        assertThat(getTimerCount("generateSurpriseIdea", "success"))
                .isEqualTo(initialState.timerCount() + 1L);
        assertThat(getTimerTotalTime("generateSurpriseIdea", "success"))
                .isGreaterThan(initialState.totalTime());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldRecordTimeAsFailureWhenExceptionIsThrown() {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(defaultTheme.getId());
        request.setContext("Contexto que causa erro para métricas");

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        MetricState initialState = getMetricState("generateIdea", "failure");

        assertThrows(RuntimeException.class, () -> {
            ideaService.generateIdea(request, false);
        });

        assertThat(getTimerCount("generateIdea", "failure"))
                .isEqualTo(initialState.timerCount() + 1L);
        assertThat(getTimerTotalTime("generateIdea", "failure"))
                .isGreaterThan(initialState.totalTime());
    }

    @Test
    @WithMockUser(username = testUserEmail)
    void shouldRecordTimeAsFailureWhenSurpriseIdeaThrowsException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        MetricState initialState = getMetricState("generateSurpriseIdea", "failure");

        assertThrows(RuntimeException.class, () -> {
            ideaService.generateSurpriseIdea();
        });

        assertThat(getTimerCount("generateSurpriseIdea", "failure"))
                .isEqualTo(initialState.timerCount() + 1L);
        assertThat(getTimerTotalTime("generateSurpriseIdea", "failure"))
                .isGreaterThan(initialState.totalTime());
    }

    private record MetricState(double counterValue, long timerCount, double totalTime) {}

    private MetricState getMetricState(String methodName, String status) {
        return new MetricState(getIdeasGeneratedCount(), getTimerCount(methodName, status), getTimerTotalTime(methodName, status));
    }

    private double getIdeasGeneratedCount() {
        return meterRegistry.find("ideas.generated.count").counter() != null ? meterRegistry.get("ideas.generated.count").counter().count() : 0.0;
    }

    private long getTimerCount(String methodName, String status) {
        return meterRegistry.find("ideas.generation.time").tag("method", methodName).tag("status", status).timer() != null ? meterRegistry.get("ideas.generation.time").tag("method", methodName).tag("status", status).timer().count() : 0L;
    }

    private double getTimerTotalTime(String methodName, String status) {
        return meterRegistry.find("ideas.generation.time").tag("method", methodName).tag("status", status).timer() != null ? meterRegistry.get("ideas.generation.time").tag("method", methodName).tag("status", status).timer().totalTime(TimeUnit.MILLISECONDS) : 0.0;
    }
}
