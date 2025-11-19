package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.dto.request.OllamaRequest;
import projeto_gerador_ideias_backend.dto.response.OllamaResponse;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaCacheableServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RequestBodySpec requestBodySpec;

    @Mock
    private RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private ResponseSpec responseSpec;

    @Mock
    private ChatProperties chatProperties;

    private OllamaCacheableService ollamaCacheableService;

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String OLLAMA_MODEL = "mistral";
    private static final String TEST_PROMPT = "Test prompt";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(webClientBuilder.baseUrl(OLLAMA_BASE_URL)).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any(OllamaRequest.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        ollamaCacheableService = new OllamaCacheableService(webClientBuilder, OLLAMA_BASE_URL, OLLAMA_MODEL, chatProperties);
    }

    @Test
    void shouldGetAiResponseSuccessfully() {
        OllamaResponse ollamaResponse = createOllamaResponse("Response content");
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        String result = ollamaCacheableService.getAiResponse(TEST_PROMPT);

        assertEquals("Response content", result);
        verify(webClient, times(1)).post();
        verify(requestBodyUriSpec, times(1)).uri("/api/chat");
        verify(requestBodySpec, times(1)).bodyValue(any(OllamaRequest.class));
    }

    @Test
    void shouldGetAiResponseBypassingCache() {
        OllamaResponse ollamaResponse = createOllamaResponse("Response content");
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        String result = ollamaCacheableService.getAiResponseBypassingCache(TEST_PROMPT);

        assertEquals("Response content", result);
        verify(webClient, times(1)).post();
        verify(requestBodyUriSpec, times(1)).uri("/api/chat");
        verify(requestBodySpec, times(1)).bodyValue(any(OllamaRequest.class));
    }

    @ParameterizedTest
    @MethodSource("provideTrimResponseContentCases")
    void shouldTrimResponseContent(String input, String expected) {
        OllamaResponse ollamaResponse = createOllamaResponse(input);
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        String result = ollamaCacheableService.getAiResponse(TEST_PROMPT);

        assertEquals(expected, result);
    }

    private static java.util.stream.Stream<Arguments> provideTrimResponseContentCases() {
        return java.util.stream.Stream.of(
                Arguments.of("  Response content with spaces  ", "Response content with spaces"),
                Arguments.of("", ""),
                Arguments.of("   ", "")
        );
    }

    @Test
    void shouldThrowExceptionWhenResponseIsNull() {
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.justOrEmpty(null));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ollamaCacheableService.getAiResponse(TEST_PROMPT);
        });

        assertEquals("Resposta nula ou inválida do Ollama (/api/chat).", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenMessageIsNull() {
        OllamaResponse ollamaResponse = new OllamaResponse();
        ollamaResponse.setMessage(null);
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ollamaCacheableService.getAiResponse(TEST_PROMPT);
        });

        assertEquals("Resposta nula ou inválida do Ollama (/api/chat).", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenContentIsNull() {
        OllamaResponse.Message message = new OllamaResponse.Message();
        message.setContent(null);
        OllamaResponse ollamaResponse = new OllamaResponse();
        ollamaResponse.setMessage(message);
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ollamaCacheableService.getAiResponse(TEST_PROMPT);
        });

        assertTrue(exception.getMessage().contains("Erro ao se comunicar com a IA (Ollama)"));
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    void shouldThrowExceptionWhenGenericExceptionOccurs() {
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.error(new RuntimeException("Network error")));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ollamaCacheableService.getAiResponse(TEST_PROMPT);
        });

        assertTrue(exception.getMessage().contains("Erro ao se comunicar com a IA (Ollama)"));
        assertNotNull(exception.getCause());
        assertEquals("Network error", exception.getCause().getMessage());
    }

    @Test
    void shouldRethrowOllamaServiceException() {
        OllamaServiceException originalException = new OllamaServiceException("Original error");
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.error(originalException));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ollamaCacheableService.getAiResponse(TEST_PROMPT);
        });

        assertEquals("Original error", exception.getMessage());
        assertSame(originalException, exception);
    }

    @Test
    void shouldCreateOllamaRequestWithCorrectModel() {
        OllamaResponse ollamaResponse = createOllamaResponse("Response");
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        ollamaCacheableService.getAiResponse(TEST_PROMPT);

        verify(requestBodySpec, times(1)).bodyValue(argThat(request -> {
            OllamaRequest req = (OllamaRequest) request;
            return OLLAMA_MODEL.equals(req.getModel()) && 
                   req.getMessages() != null && 
                   req.getMessages().size() == 1 &&
                   "user".equals(req.getMessages().get(0).getRole()) &&
                   TEST_PROMPT.equals(req.getMessages().get(0).getContent());
        }));
    }

    @Test
    void shouldCallWebClientForEachRequest() {
        OllamaResponse ollamaResponse = createOllamaResponse("Response content");
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        String firstResult = ollamaCacheableService.getAiResponse(TEST_PROMPT);
        String secondResult = ollamaCacheableService.getAiResponse(TEST_PROMPT);

        assertEquals("Response content", firstResult);
        assertEquals("Response content", secondResult);
        verify(webClient, atLeastOnce()).post();
    }

    @Test
    void shouldNotUseCacheWhenBypassing() {
        OllamaResponse ollamaResponse = createOllamaResponse("Response content");
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(Mono.just(ollamaResponse));

        ollamaCacheableService.getAiResponseBypassingCache(TEST_PROMPT);
        ollamaCacheableService.getAiResponseBypassingCache(TEST_PROMPT);

        verify(webClient, times(2)).post();
    }


    private OllamaResponse createOllamaResponse(String content) {
        OllamaResponse.Message message = new OllamaResponse.Message();
        message.setRole("assistant");
        message.setContent(content);
        OllamaResponse response = new OllamaResponse();
        response.setMessage(message);
        return response;
    }
}


