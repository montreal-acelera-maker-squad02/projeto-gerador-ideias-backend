package projeto_gerador_ideias_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.IdeaRepository;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdeaServiceTest {

    public MockWebServer mockWebServer;

    @Mock
    private IdeaRepository ideaRepository;

    private IdeaService ideaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpEach() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient.Builder webClientBuilder = WebClient.builder();
        ideaService = new IdeaService(ideaRepository, webClientBuilder, baseUrl);
        ReflectionTestUtils.setField(ideaService, "ollamaModel", "mistral-test");
    }

    @AfterEach
    void tearDownEach() throws IOException {
        mockWebServer.shutdown();
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
        verify(ideaRepository).save(any(Idea.class)); // Verificação correta aqui
    }

    @Test
    void shouldRejectIdeaWhenModerationIsDangerous() throws Exception {
        IdeaRequest request = new IdeaRequest();
        request.setTheme(Theme.TECNOLOGIA);
        request.setContext("Como fazer phishing");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("PERIGOSO"))
                .addHeader("Content-Type", "application/json"));

        IdeaResponse response = ideaService.generateIdea(request);

        assertNotNull(response);
        assertEquals("Desculpe, não posso gerar ideias sobre esse tema.", response.getContent());
        assertEquals(1, mockWebServer.getRequestCount());
        verify(ideaRepository, never()).save(any(Idea.class));
    }

    @Test
    void shouldHandleOllamaException() {
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
}