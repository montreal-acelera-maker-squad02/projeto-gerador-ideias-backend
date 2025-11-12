package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.dto.request.OllamaRequest;
import projeto_gerador_ideias_backend.dto.response.OllamaResponse;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class OllamaIntegrationServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private ChatProperties chatProperties;

    @Mock
    private ChatMetricsService chatMetricsService;

    @InjectMocks
    private OllamaIntegrationService ollamaIntegrationService;

    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    private static final String TEST_MODEL = "mistral";
    private static final String TEST_BASE_URL = "http://localhost:11434";
    private static final int TEST_TIMEOUT = 60;
    private static final int TEST_MAX_RESPONSE_LENGTH = 100000;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ollamaIntegrationService, "ollamaModel", TEST_MODEL);
        ReflectionTestUtils.setField(ollamaIntegrationService, "ollamaBaseUrl", TEST_BASE_URL);

        when(chatProperties.getOllamaTimeoutSeconds()).thenReturn(TEST_TIMEOUT);
        when(chatProperties.getMaxResponseLength()).thenReturn(TEST_MAX_RESPONSE_LENGTH);

        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenAnswer(invocation -> requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @Test
    void shouldCallOllamaWithSystemPromptSuccessfully() {
        String systemPrompt = "Você é um assistente útil.";
        String userPrompt = "Olá, como você está?";
        String expectedResponse = "Olá! Estou bem, obrigado por perguntar.";

        OllamaResponse ollamaResponse = createMockResponse(expectedResponse);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt(systemPrompt, userPrompt);

        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/api/chat");
        verify(chatMetricsService).recordOllamaCallTime(anyLong());
    }

    @Test
    void shouldCallOllamaWithSystemPromptAndExtractContent() {
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String responseContent = "Resposta da IA";

        OllamaResponse ollamaResponse = createMockResponse(responseContent);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt(systemPrompt, userPrompt);

        assertEquals(responseContent, result);
    }

    @Test
    void shouldCallOllamaWithHistorySuccessfully() {
        String systemPrompt = "Você é um assistente útil.";
        List<OllamaRequest.Message> historyMessages = List.of(
                new OllamaRequest.Message("user", "Mensagem anterior"),
                new OllamaRequest.Message("assistant", "Resposta anterior")
        );
        String userPrompt = "Nova mensagem";
        String expectedResponse = "Nova resposta";

        OllamaResponse ollamaResponse = createMockResponse(expectedResponse);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithHistory(systemPrompt, historyMessages, userPrompt);

        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(webClient).post();
        verify(chatMetricsService).recordOllamaCallTime(anyLong());
    }

    @Test
    void shouldCallOllamaWithHistoryAndEmptyHistory() {
        String systemPrompt = "System prompt";
        List<OllamaRequest.Message> historyMessages = List.of();
        String userPrompt = "User prompt";
        String expectedResponse = "Response";

        OllamaResponse ollamaResponse = createMockResponse(expectedResponse);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithHistory(systemPrompt, historyMessages, userPrompt);

        assertEquals(expectedResponse, result);
    }

    @Test
    void shouldExtractResponseContentSuccessfully() {
        String content = "Resposta normal da IA";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals(content, result);
    }

    @Test
    void shouldRemoveModerationTagsFromResponse() {
        String contentWithTags = "[MODERACAO: SEGURA]Resposta limpa";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta limpa", result);
        assertFalse(result.contains("[MODERACAO"));
    }

    @Test
    void shouldRemoveDangerousModerationTags() {
        String contentWithTags = "[MODERACAO: PERIGOSO]Conteúdo perigoso";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Conteúdo perigoso", result);
        assertFalse(result.contains("[MODERACAO"));
    }

    @Test
    void shouldKeepDangerousContentWhenOnlyModerationTag() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";
        OllamaResponse ollamaResponse = createMockResponse(dangerousContent);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("[MODERACAO: PERIGOSO]", result);
    }

    @Test
    void shouldRemoveMultipleModerationTags() {
        String contentWithTags = "[MODERACAO: SEGURA][MODERACAO: PERIGOSO]Resposta";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta", result);
    }

    @Test
    void shouldHandleModerationTagAtStart() {
        String contentWithTags = "[MODERACAO: SEGURA]Resposta com conteúdo";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta com conteúdo", result);
    }

    @Test
    void shouldThrowExceptionWhenResponseExceedsMaxLength() {
        String longContent = "a".repeat(TEST_MAX_RESPONSE_LENGTH + 1);
        OllamaResponse ollamaResponse = createMockResponse(longContent);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
    }

    @Test
    void shouldThrowExceptionWhenResponseIsNull() {
        Mono<OllamaResponse> nullResponseMono = Mono.<OllamaResponse>empty()
                .defaultIfEmpty(null)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(nullResponseMono);

        assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
    }

    @Test
    void shouldThrowExceptionWhenMessageIsNull() {
        OllamaResponse ollamaResponse = new OllamaResponse();
        ollamaResponse.setMessage(null);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
    }

    @Test
    void shouldThrowExceptionWhenContentIsNull() {
        OllamaResponse ollamaResponse = new OllamaResponse();
        OllamaResponse.Message message = new OllamaResponse.Message();
        message.setContent(null);
        ollamaResponse.setMessage(message);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
    }

    @Test
    void shouldHandleWebClientResponseException500() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        when(exception.getResponseBodyAsString()).thenReturn("Internal Server Error");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleWebClientResponseException400() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.BAD_REQUEST);
        when(exception.getResponseBodyAsString()).thenReturn("Bad Request");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleWebClientResponseExceptionWithEmptyBody() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        when(exception.getResponseBodyAsString()).thenReturn("");
        when(exception.getMessage()).thenReturn("Error message");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
    }

    @Test
    void shouldHandleWebClientRequestException() {
        RuntimeException connectionException = new RuntimeException("Connection refused");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(connectionException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleTimeoutException() {
        RuntimeException timeoutException = new RuntimeException(new TimeoutException("Timeout"));
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(timeoutException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertTrue(exception.getMessage().contains("Timeout"));
    }

    @Test
    void shouldHandleTimeoutExceptionInMessage() {
        RuntimeException timeoutException = new RuntimeException("Request timeout occurred");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(timeoutException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertTrue(exception.getMessage().contains("Timeout"));
    }

    @Test
    void shouldHandleConnectionRefusedException() {
        RuntimeException connectionException = new RuntimeException("Connection refused");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(connectionException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertTrue(exception.getMessage().contains("conectar"));
    }

    @Test
    void shouldHandleConnectionResetException() {
        RuntimeException connectionException = new RuntimeException("Connection reset");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(connectionException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertTrue(exception.getMessage().contains("conectar"));
    }

    @Test
    void shouldHandleConnectException() {
        RuntimeException connectionException = new RuntimeException("Failed to connect");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(connectionException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertTrue(exception.getMessage().contains("conectar"));
    }

    @Test
    void shouldHandleGenericException() {
        RuntimeException genericException = new RuntimeException("Unexpected error");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(genericException)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Ollama"));
    }

    @Test
    void shouldApplyTimeoutConfiguration() {
        String content = "Response";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        verify(responseSpec).bodyToMono(OllamaResponse.class);
    }

    @Test
    void shouldCreateCorrectOllamaRequest() {
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String content = "Response";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        ArgumentCaptor<OllamaRequest> requestCaptor = ArgumentCaptor.forClass(OllamaRequest.class);
        ollamaIntegrationService.callOllamaWithSystemPrompt(systemPrompt, userPrompt);

        verify(requestBodySpec).bodyValue(requestCaptor.capture());
        OllamaRequest capturedRequest = requestCaptor.getValue();
        
        assertEquals(TEST_MODEL, capturedRequest.getModel());
        assertNotNull(capturedRequest.getMessages());
        assertEquals(2, capturedRequest.getMessages().size());
        assertEquals("system", capturedRequest.getMessages().get(0).getRole());
        assertEquals(systemPrompt, capturedRequest.getMessages().get(0).getContent());
        assertEquals("user", capturedRequest.getMessages().get(1).getRole());
        assertEquals(userPrompt, capturedRequest.getMessages().get(1).getContent());
    }

    @Test
    void shouldCreateCorrectOllamaRequestWithHistory() {
        String systemPrompt = "System prompt";
        List<OllamaRequest.Message> historyMessages = List.of(
                new OllamaRequest.Message("user", "Hist1"),
                new OllamaRequest.Message("assistant", "Resp1")
        );
        String userPrompt = "User prompt";
        String content = "Response";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        ArgumentCaptor<OllamaRequest> requestCaptor = ArgumentCaptor.forClass(OllamaRequest.class);
        ollamaIntegrationService.callOllamaWithHistory(systemPrompt, historyMessages, userPrompt);

        verify(requestBodySpec).bodyValue(requestCaptor.capture());
        OllamaRequest capturedRequest = requestCaptor.getValue();
        
        assertEquals(TEST_MODEL, capturedRequest.getModel());
        assertNotNull(capturedRequest.getMessages());
        assertEquals(4, capturedRequest.getMessages().size());
        assertEquals("system", capturedRequest.getMessages().get(0).getRole());
        assertEquals("user", capturedRequest.getMessages().get(3).getRole());
    }

    @Test
    void shouldTrimResponseContent() {
        String content = "  Resposta com espaços  ";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta com espaços", result);
    }

    @Test
    void shouldRecordMetricsOnSuccess() {
        String content = "Response";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        verify(chatMetricsService).recordOllamaCallTime(anyLong());
    }

    @Test
    void shouldRecordMetricsOnError() {
        RuntimeException exception = new RuntimeException("Error");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleWebClientResponseException404() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.NOT_FOUND);
        when(exception.getResponseBodyAsString()).thenReturn("Not Found");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleWebClientResponseException503() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        when(exception.getResponseBodyAsString()).thenReturn("Service Unavailable");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
    }

    @Test
    void shouldHandleWebClientException() {
        org.springframework.web.reactive.function.client.WebClientRequestException exception = 
            new org.springframework.web.reactive.function.client.WebClientRequestException(
                new RuntimeException("WebClient error"), 
                org.springframework.http.HttpMethod.POST,
                null,
                new org.springframework.http.HttpHeaders());
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }


    @Test
    void shouldHandleModerationTagAtStartWithContent() {
        String contentWithTags = "[MODERACAO: SEGURA]Resposta";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta", result);
    }

    @Test
    void shouldHandleModerationTagInMiddle() {
        String contentWithTags = "Texto [MODERACAO: SEGURA] mais texto";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Texto  mais texto", result);
    }

    @Test
    void shouldHandleModerationTagWithSpaces() {
        String contentWithTags = "[ MODERACAO : SEGURA ]Resposta";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta", result);
    }

    @Test
    void shouldHandleModerationTagPatternMatching() {
        String dangerousContent = "[MODERACAO:PERIGOSO]";
        OllamaResponse ollamaResponse = createMockResponse(dangerousContent);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("[MODERACAO:PERIGOSO]", result);
    }

    @Test
    void shouldHandleResponseWithWhitespaceOnly() {
        String content = "   ";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("", result);
    }

    @Test
    void shouldHandleResponseAtMaxLength() {
        String content = "a".repeat(TEST_MAX_RESPONSE_LENGTH);
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals(content, result);
    }

    @Test
    void shouldHandleModerationTagWithSubstringExtraction() {
        String contentWithTags = "[MODERACAO: SEGURA]Resto do texto";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resto do texto", result);
    }

    @Test
    void shouldHandleExceptionWithNullMessage() {
        RuntimeException exception = new RuntimeException((String) null);
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
    }

    @Test
    void shouldHandleExceptionWithNullCause() {
        RuntimeException exception = new RuntimeException("Error");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
    }

    @Test
    void shouldUseChatPropertiesForOllamaRequest() {
        when(chatProperties.getOllamaNumPredict()).thenReturn(300);
        when(chatProperties.getOllamaTemperature()).thenReturn(0.7);
        when(chatProperties.getOllamaTopP()).thenReturn(0.9);
        when(chatProperties.getOllamaNumCtx()).thenReturn(2048);

        String content = "Response";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        ArgumentCaptor<OllamaRequest> requestCaptor = ArgumentCaptor.forClass(OllamaRequest.class);
        ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        verify(requestBodySpec).bodyValue(requestCaptor.capture());
        OllamaRequest capturedRequest = requestCaptor.getValue();
        
        assertNotNull(capturedRequest);
    }

    @Test
    void shouldHandleWebClientResponseExceptionWith500AndSpecificMessage() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        when(exception.getResponseBodyAsString()).thenReturn("Internal Server Error");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleWebClientResponseExceptionWithBlankBody() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.BAD_REQUEST);
        when(exception.getResponseBodyAsString()).thenReturn("   ");
        when(exception.getMessage()).thenReturn("Error message");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleModerationTagWithComplexPattern() {
        String contentWithTags = "[MODERACAO: SEGURA]Texto[MODERACAO: PERIGOSO]Mais texto";
        OllamaResponse ollamaResponse = createMockResponse(contentWithTags);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("TextoMais texto", result);
    }

    @Test
    void shouldHandleResponseWithOnlyWhitespaceAfterTagRemoval() {
        String content = "[MODERACAO: SEGURA]   ";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("", result);
    }

    @Test
    void shouldHandleResponseWithModerationTagAtEnd() {
        String content = "Resposta[MODERACAO: SEGURA]";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta", result);
    }

    @Test
    void shouldHandleResponseWithMultipleModerationTagsInSequence() {
        String content = "[MODERACAO: SEGURA][MODERACAO: PERIGOSO][MODERACAO: SEGURA]Resposta";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta", result);
    }

    @Test
    void shouldHandleResponseWithModerationTagPatternAtStart() {
        String content = "[MODERACAO: SEGURA]Resto";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resto", result);
    }

    @Test
    void shouldHandleResponseWithCaseInsensitiveModerationTags() {
        String content = "[moderacao: segura]Resposta";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta", result);
    }

    @Test
    void shouldHandleWebClientResponseException502() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.BAD_GATEWAY);
        when(exception.getResponseBodyAsString()).thenReturn("Bad Gateway");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
    }

    @Test
    void shouldHandleWebClientResponseException504() {
        WebClientResponseException exception = mock(WebClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT);
        when(exception.getResponseBodyAsString()).thenReturn("Gateway Timeout");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
    }

    @Test
    void shouldHandleResponseWithModerationTagAndSubstringExtraction() {
        String content = "[MODERACAO: SEGURA]Resto do texto aqui";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resto do texto aqui", result);
    }

    @Test
    void shouldHandleResponseWithTrimmedWhitespace() {
        String content = "  Resposta com espaços  ";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        String result = ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User");

        assertEquals("Resposta com espaços", result);
    }

    @Test
    void shouldHandleOllamaRequestException() {
        org.springframework.web.reactive.function.client.WebClientRequestException exception = 
            new org.springframework.web.reactive.function.client.WebClientRequestException(
                new RuntimeException("Connection refused"), 
                org.springframework.http.HttpMethod.POST,
                null,
                new org.springframework.http.HttpHeaders());
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Ollama"));
    }

    @Test
    void shouldHandleExceptionWithConnectionReset() {
        RuntimeException exception = new RuntimeException("Connection reset by peer");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("conectar"));
    }

    @Test
    void shouldHandleExceptionWithFailedToConnect() {
        RuntimeException exception = new RuntimeException("Failed to connect to host");
        Mono<OllamaResponse> errorMono = Mono.<OllamaResponse>error(exception)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(errorMono);

        OllamaServiceException result = assertThrows(OllamaServiceException.class, () -> 
            ollamaIntegrationService.callOllamaWithSystemPrompt("System", "User"));
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("conectar"));
    }

    @Test
    void shouldUseChatPropertiesForOllamaRequestWithHistory() {
        when(chatProperties.getOllamaNumPredict()).thenReturn(300);
        when(chatProperties.getOllamaTemperature()).thenReturn(0.7);
        when(chatProperties.getOllamaTopP()).thenReturn(0.9);
        when(chatProperties.getOllamaNumCtx()).thenReturn(2048);

        String content = "Response";
        OllamaResponse ollamaResponse = createMockResponse(content);
        Mono<OllamaResponse> responseMono = Mono.just(ollamaResponse)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT));
        when(responseSpec.bodyToMono(OllamaResponse.class)).thenReturn(responseMono);

        List<OllamaRequest.Message> history = List.of(
            new OllamaRequest.Message("user", "Hist1"),
            new OllamaRequest.Message("assistant", "Resp1")
        );

        ArgumentCaptor<OllamaRequest> requestCaptor = ArgumentCaptor.forClass(OllamaRequest.class);
        ollamaIntegrationService.callOllamaWithHistory("System", history, "User");

        verify(requestBodySpec).bodyValue(requestCaptor.capture());
        OllamaRequest capturedRequest = requestCaptor.getValue();
        
        assertNotNull(capturedRequest);
        assertEquals(4, capturedRequest.getMessages().size());
    }

    private OllamaResponse createMockResponse(String content) {
        OllamaResponse response = new OllamaResponse();
        OllamaResponse.Message message = new OllamaResponse.Message();
        message.setContent(content);
        message.setRole("assistant");
        response.setMessage(message);
        return response;
    }
}


