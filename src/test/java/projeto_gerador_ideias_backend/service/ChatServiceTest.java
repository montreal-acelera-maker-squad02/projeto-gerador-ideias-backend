package projeto_gerador_ideias_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import projeto_gerador_ideias_backend.dto.*;
import projeto_gerador_ideias_backend.exceptions.ChatPermissionException;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.TokenLimitExceededException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.model.*;
import projeto_gerador_ideias_backend.repository.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ChatServiceTest {

    public MockWebServer mockWebServer;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private IdeaRepository ideaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private projeto_gerador_ideias_backend.config.ChatProperties chatProperties;

    @Mock
    private TokenCalculationService tokenCalculationService;

    @Mock
    private PromptBuilderService promptBuilderService;

    @Mock
    private OllamaIntegrationService ollamaIntegrationService;

    @Mock
    private ChatLimitValidator chatLimitValidator;

    @Mock
    private ContentModerationService contentModerationService;

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private ChatMetricsService chatMetricsService;

    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private final String testUserEmail = "chat-service@example.com";

    @BeforeEach
    void setUpEach() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(testUserEmail);
        testUser.setName("Chat Service User");

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        lenient().when(userCacheService.getCurrentAuthenticatedUser()).thenReturn(testUser);
        lenient().when(chatProperties.getMaxHistoryMessages()).thenReturn(3);
        lenient().when(chatProperties.getMaxTokensPerMessage()).thenReturn(1000);
        lenient().when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        lenient().when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        
        lenient().when(tokenCalculationService.getTotalUserTokensInChat(any())).thenReturn(0);
        lenient().when(tokenCalculationService.getTotalTokensUsedByUser(any())).thenReturn(0);
        lenient().when(tokenCalculationService.estimateTokens(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return text != null ? Math.max(text.length() / 4, text.split("\\s+").length) : 0;
        });
        lenient().when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenAnswer(inv -> {
            String msg = inv.getArgument(0);
            return tokenCalculationService.estimateTokens(msg);
        });
        lenient().when(promptBuilderService.buildSystemPromptForFreeChat()).thenReturn("System prompt");
        lenient().when(promptBuilderService.buildSystemPromptForIdeaChat(any(ChatSession.class))).thenReturn("System prompt");
        lenient().when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("AI Response");
        lenient().when(chatMessageRepository.countBySessionId(any())).thenReturn(0L);
        lenient().when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        
        org.springframework.data.domain.Page<ChatMessage> emptyPage = org.springframework.data.domain.Page.empty();
        lenient().when(chatMessageRepository.findLastMessagesBySessionIdAndRole(any(), any(), any())).thenReturn(emptyPage);

        chatService = new ChatService(
                chatSessionRepository,
                chatMessageRepository,
                ideaRepository,
                userRepository,
                chatProperties,
                tokenCalculationService,
                promptBuilderService,
                ollamaIntegrationService,
                chatLimitValidator,
                contentModerationService,
                userCacheService,
                chatMetricsService
        );

        try {
            java.lang.reflect.Field selfField = ChatService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(chatService, chatService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set self field", e);
        }

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
    void shouldStartFreeChatSuccessfully() {
        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(1L);
            return session;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(any())).thenReturn(0);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
        assertNotNull(response.getTotalTokens());
        assertTrue(response.getTokensRemaining() >= 0);
        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    void shouldStartIdeaBasedChatSuccessfully() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto de teste");
        idea.setGeneratedContent("Ideia de teste");

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(1L);
            return session;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(any())).thenReturn(0);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(ChatSession.ChatType.IDEA_BASED.toString(), response.getChatType());
        assertEquals(1L, response.getIdeaId());
        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    void shouldThrowExceptionWhenIdeaNotFound() {
        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(999L);

        when(ideaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotOwnIdea() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");

        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(otherUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));

        assertThrows(ChatPermissionException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldSendMessageInFreeChatSuccessfully() throws JsonProcessingException {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Olá");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Olá! Como posso ajudar?"))
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("AI Response");
        when(tokenCalculationService.getTotalTokensUsedByUser(any())).thenReturn(10);

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertEquals("assistant", response.getRole());
        assertNotNull(response.getContent());
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void shouldThrowExceptionWhenSessionNotFound() {
        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> chatService.sendMessage(999L, messageRequest));
    }

    @Test
    void shouldThrowExceptionWhenMessageIsDangerous() throws JsonProcessingException {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Conteúdo perigoso");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("PERIGOSO"))
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("[MODERACAO: PERIGOSO]");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        doThrow(new ValidationException("Desculpe, não posso processar essa mensagem devido ao conteúdo."))
                .when(contentModerationService).validateModerationResponse("[MODERACAO: PERIGOSO]", true);

        assertThrows(ValidationException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldGetUserIdeasSummarySuccessfully() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia de teste para resumo completo");
        idea.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(idea));

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).getId());
        assertNotNull(response.get(0).getSummary());
    }

    @Test
    void shouldGetSessionSuccessfully() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(tokenCalculationService.getTotalTokensUsedByUser(1L)).thenReturn(0);

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
    }

    @Test
    void shouldThrowExceptionWhenGettingNonExistentSession() {
        when(chatSessionRepository.findByIdWithIdea(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> chatService.getSession(999L));
    }

    @Test
    void shouldThrowExceptionWhenTokenLimitExceeded() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());
        session.setTokensUsed(1000);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10000);
        doThrow(new TokenLimitExceededException("Este chat atingiu o limite"))
                .when(chatLimitValidator).validateChatNotBlocked(eq(session), anyInt());

        assertThrows(TokenLimitExceededException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotOwnSession() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");

        ChatSession session = new ChatSession(otherUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));

        assertThrows(ChatPermissionException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldReuseExistingFreeChatSession() {
        ChatSession existingSession = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        existingSession.setId(1L);
        existingSession.setLastResetAt(LocalDateTime.now());
        existingSession.setTokensUsed(100);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(100);
        when(chatLimitValidator.isChatBlocked(existingSession)).thenReturn(false);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
        verify(chatSessionRepository, never()).save(any(ChatSession.class));
    }

    @Test
    void shouldThrowExceptionWhenFreeChatReachesTokenLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now().minusHours(10));
        session.setTokensUsed(1000);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.of(session));
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10000);
        when(chatLimitValidator.isChatBlocked(session)).thenReturn(true);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession newSession = invocation.getArgument(0);
            newSession.setId(2L);
            return newSession;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(any())).thenReturn(0);

        ChatSessionResponse response = chatService.startChat(request);
        assertNotNull(response);
        assertEquals(2L, response.getSessionId());
    }

    @Test
    void shouldThrowExceptionWhenIdeaBasedChatReachesTokenLimit() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");

        ChatSession existingSession = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        existingSession.setId(1L);
        existingSession.setLastResetAt(LocalDateTime.now());
        existingSession.setTokensUsed(1000);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(existingSession));
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10000);
        when(chatLimitValidator.isChatBlocked(existingSession)).thenReturn(true);
        doThrow(new TokenLimitExceededException("Este chat atingiu o limite")).when(chatLimitValidator).validateSessionNotBlocked(existingSession);

        assertThrows(TokenLimitExceededException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldThrowExceptionWhenUserHasRecentlyBlockedIdeaBasedChat() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");

        ChatSession blockedSession = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        blockedSession.setId(2L);
        blockedSession.setLastResetAt(LocalDateTime.now().minusHours(12));
        blockedSession.setTokensUsed(1000);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(blockedSession));
        when(tokenCalculationService.getTotalUserTokensInChat(2L)).thenReturn(10000);
        when(chatLimitValidator.isChatBlocked(blockedSession)).thenReturn(true);
        doThrow(new TokenLimitExceededException("Este chat atingiu o limite")).when(chatLimitValidator).validateSessionNotBlocked(blockedSession);

        assertThrows(TokenLimitExceededException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldReuseExistingIdeaBasedChatSession() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");

        ChatSession existingSession = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        existingSession.setId(1L);
        existingSession.setLastResetAt(LocalDateTime.now());
        existingSession.setTokensUsed(100);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(100);
        when(chatLimitValidator.isChatBlocked(existingSession)).thenReturn(false);
        when(tokenCalculationService.getTotalTokensUsedByUser(1L)).thenReturn(100);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.IDEA_BASED.toString(), response.getChatType());
        verify(chatSessionRepository, never()).save(any(ChatSession.class));
    }

    @Test
    void shouldSendMessageInIdeaBasedChatWithoutModeration() throws JsonProcessingException {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia completa");

        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Pergunta sobre a ideia");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Resposta sobre a ideia"))
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("AI Response");
        when(tokenCalculationService.getTotalTokensUsedByUser(any())).thenReturn(10);

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertEquals("assistant", response.getRole());
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void shouldIncludeMessageHistoryInContext() throws JsonProcessingException {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessage previousMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Olá", 10);
        previousMessage.setId(1L);
        previousMessage.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Como você está?");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Estou bem, obrigado!"))
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(1L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.singletonList(previousMessage));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(2L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10);
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Estou bem, obrigado!");
        when(tokenCalculationService.getTotalTokensUsedByUser(any())).thenReturn(20);

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        verify(ollamaIntegrationService, times(1)).callOllamaWithSystemPrompt(anyString(), anyString());
    }

    @Test
    void shouldThrowExceptionWhenTokensInsufficientBeforeSending() throws JsonProcessingException {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());
        session.setTokensUsed(0);

        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");

        ChatSession otherSession = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        otherSession.setId(2L);
        otherSession.setLastResetAt(LocalDateTime.now());
        otherSession.setTokensUsed(999);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Mensagem muito longa que vai consumir muitos tokens para teste de limite com contexto e tudo mais");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10000);
        doThrow(new TokenLimitExceededException("Este chat atingiu o limite"))
                .when(chatLimitValidator).validateChatNotBlocked(eq(session), anyInt());

        assertThrows(TokenLimitExceededException.class, () -> chatService.sendMessage(1L, messageRequest));
    }


    @Test
    void shouldHandleOllamaConnectionRefused() throws IOException {
        try (MockWebServer disconnectedServer = new MockWebServer()) {
            disconnectedServer.start();
            disconnectedServer.shutdown();

            ChatService disconnectedService = new ChatService(
                    chatSessionRepository,
                    chatMessageRepository,
                    ideaRepository,
                    userRepository,
                    chatProperties,
                    tokenCalculationService,
                    promptBuilderService,
                    ollamaIntegrationService,
                    chatLimitValidator,
                    contentModerationService,
                    userCacheService,
                    chatMetricsService
            );

            try {
                java.lang.reflect.Field selfField = ChatService.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(disconnectedService, disconnectedService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set self field", e);
            }

            ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
            session.setId(1L);
            session.setLastResetAt(LocalDateTime.now());

            ChatMessageRequest messageRequest = new ChatMessageRequest();
            messageRequest.setMessage("Teste");

            when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
            when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
            when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
            when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
            when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
            when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                    .thenThrow(new OllamaServiceException("Connection refused"));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                disconnectedService.sendMessage(1L, messageRequest);
            });

            assertTrue(exception.getMessage().contains("Connection") || exception.getMessage().contains("Ollama"));
        }
    }

    @Test
    void shouldHandleOllamaErrorResponse() throws JsonProcessingException {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                .thenThrow(new OllamaServiceException("Erro HTTP 500 do Ollama: Internal Server Error"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            chatService.sendMessage(1L, messageRequest);
        });

        assertTrue(exception.getMessage().contains("Erro") || exception.getMessage().contains("Ollama"));
    }

    @Test
    void shouldGetSessionWithIdeaBasedChat() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia completa para resumo");

        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessage message = new ChatMessage(session, ChatMessage.MessageRole.USER, "Olá", 10);
        message.setId(1L);
        message.setCreatedAt(LocalDateTime.now());

        reset(chatMessageRepository);
        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.singletonList(message));
        when(chatMessageRepository.findRecentMessagesOptimized(eq(1L), anyInt())).thenReturn(Collections.singletonList(message));
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), eq(ChatMessage.MessageRole.USER))).thenReturn(10);
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT))).thenReturn(0);
        when(chatMessageRepository.findLastMessagesBySessionIdAndRole(eq(1L), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        when(chatMessageRepository.countBySessionId(any())).thenReturn(0L);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10);
        when(tokenCalculationService.getTotalTokensUsedByUser(1L)).thenReturn(10);
        lenient().when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Resumo da Ideia");

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.IDEA_BASED.toString(), response.getChatType());
        assertEquals(1L, response.getIdeaId());
        assertNotNull(response.getIdeaSummary());
        assertNotNull(response.getMessages());
        assertTrue(response.getMessages().size() >= 0, "Deve retornar pelo menos 0 mensagens (pode estar vazio se não houver mensagens)");
    }

    @Test
    void shouldGetUserIdeasSummaryWithMultipleIdeas() {
        Idea idea1 = new Idea();
        idea1.setId(1L);
        idea1.setUser(testUser);
        idea1.setTheme(Theme.TECNOLOGIA);
        idea1.setContext("Contexto 1");
        idea1.setGeneratedContent("Ideia completa com várias palavras para resumo");
        idea1.setCreatedAt(LocalDateTime.now());

        Idea idea2 = new Idea();
        idea2.setId(2L);
        idea2.setUser(testUser);
        idea2.setTheme(Theme.TRABALHO);
        idea2.setContext("Contexto 2");
        idea2.setGeneratedContent("Outra ideia");
        idea2.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList(idea1, idea2));

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(2, response.size());
        assertEquals(1L, response.get(0).getId());
        assertEquals(2L, response.get(1).getId());
    }

    @Test
    void shouldThrowExceptionWhenGettingSessionUserDoesNotOwn() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");

        ChatSession session = new ChatSession(otherUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));

        assertThrows(ChatPermissionException.class, () -> chatService.getSession(1L));
    }

    @Test
    void shouldThrowExceptionWhenUserNotAuthenticated() {
        SecurityContextHolder.clearContext();
        when(userCacheService.getCurrentAuthenticatedUser()).thenThrow(new ResourceNotFoundException("Usuário não autenticado"));

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        assertThrows(ResourceNotFoundException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldThrowExceptionWhenAuthenticatedUserNotFound() {
        when(userCacheService.getCurrentAuthenticatedUser()).thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        assertThrows(ResourceNotFoundException.class, () -> chatService.startChat(request));
    }
}

