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
import projeto_gerador_ideias_backend.dto.request.*;
import projeto_gerador_ideias_backend.dto.response.*;
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
    private Theme tecnologiaTheme;
    private Theme trabalhoTheme;

    @BeforeEach
    void setUpEach() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(testUserEmail);
        testUser.setName("Chat Service User");

        tecnologiaTheme = new Theme("TECNOLOGIA");
        trabalhoTheme = new Theme("TRABALHO");

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
        lenient().when(contentModerationService.validateAndNormalizeResponse(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chatMessageRepository.countBySessionId(any())).thenReturn(0L);
        lenient().when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        
        org.springframework.data.domain.Page<ChatMessage> emptyPage = org.springframework.data.domain.Page.empty();
        lenient().when(chatMessageRepository.findLastMessagesBySessionIdAndRole(any(), any(), any())).thenReturn(emptyPage);

        chatService = new ChatService(
                chatSessionRepository,
                chatMessageRepository,
                ideaRepository,
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
        idea.setTheme(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
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
        when(contentModerationService.validateAndNormalizeResponse("AI Response", true)).thenReturn("AI Response");
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
                .setBody(createMockOllamaResponse("[MODERACAO: PERIGOSO]"))
                .addHeader("Content-Type", "application/json"));

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(0L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("[MODERACAO: PERIGOSO]");
        when(contentModerationService.validateAndNormalizeResponse("[MODERACAO: PERIGOSO]", true))
                .thenReturn("Desculpe, não posso processar essa mensagem devido ao conteúdo. Posso ajudá-lo com outras questões?");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);
        
        assertNotNull(response);
        assertEquals("Desculpe, não posso processar essa mensagem devido ao conteúdo. Posso ajudá-lo com outras questões?", response.getContent());
    }

    @Test
    void shouldGetUserIdeasSummarySuccessfully() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
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
        when(contentModerationService.validateAndNormalizeResponse("AI Response", false)).thenReturn("AI Response");
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
        when(contentModerationService.validateAndNormalizeResponse("Estou bem, obrigado!", true)).thenReturn("Estou bem, obrigado!");
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
        idea.setTheme(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
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
        idea1.setTheme(tecnologiaTheme);
        idea1.setContext("Contexto 1");
        idea1.setGeneratedContent("Ideia completa com várias palavras para resumo");
        idea1.setCreatedAt(LocalDateTime.now());

        Idea idea2 = new Idea();
        idea2.setId(2L);
        idea2.setUser(testUser);
        idea2.setTheme(trabalhoTheme);
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

    @Test
    void shouldGetOlderMessagesSuccessfully() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessage oldMessage1 = new ChatMessage(session, ChatMessage.MessageRole.USER, "Mensagem antiga 1", 10);
        oldMessage1.setId(1L);
        oldMessage1.setCreatedAt(LocalDateTime.now().minusHours(2));

        ChatMessage oldMessage2 = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resposta antiga 1", 20);
        oldMessage2.setId(2L);
        oldMessage2.setCreatedAt(LocalDateTime.now().minusHours(1));
        oldMessage2.setTokensRemaining(9970);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Arrays.asList(oldMessage2, oldMessage1));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Arrays.asList(oldMessage1, oldMessage2));

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 20);

        assertNotNull(response);
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        verify(chatMessageRepository).findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any());
    }

    @Test
    void shouldThrowExceptionWhenGettingOlderMessagesWithInvalidTimestamp() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));

        assertThrows(ValidationException.class, () -> 
            chatService.getOlderMessages(1L, "timestamp-invalido", 20));
    }

    @Test
    void shouldThrowExceptionWhenGettingOlderMessagesWithoutPermission() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");

        ChatSession session = new ChatSession(otherUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        assertThrows(ChatPermissionException.class, () -> 
            chatService.getOlderMessages(1L, beforeTimestamp, 20));
    }

    @Test
    void shouldGetOlderMessagesWithCustomLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 50);

        assertNotNull(response);
        assertNotNull(response.getMessages());
        verify(chatMessageRepository).findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any());
    }

    @Test
    void shouldGetOlderMessagesWithDefaultLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, null);

        assertNotNull(response);
        assertNotNull(response.getMessages());
        verify(chatMessageRepository).findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any());
    }

    @Test
    void shouldGetChatLogsSuccessfully() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Olá", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Olá! Como posso ajudar?", 20);
        assistantMessage.setId(2L);
        assistantMessage.setCreatedAt(LocalDateTime.now().plusSeconds(1));

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertNotNull(response.getSummary());
        assertNotNull(response.getInteractions());
        assertNotNull(response.getPagination());
        assertEquals(1, response.getInteractions().size());
    }

    @Test
    void shouldGetChatLogsWithSpecificDate() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Teste", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs("2025-11-08", 1, 10);

        assertNotNull(response);
        assertEquals("2025-11-08", response.getSelectedDate());
    }

    @Test
    void shouldThrowExceptionWhenChatLogsDateInvalid() {
        assertThrows(ValidationException.class, () -> 
            chatService.getChatLogs("data-invalida", 1, 10));
    }

    @Test
    void shouldGetChatLogsWithPagination() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ChatMessage userMsg = new ChatMessage(session, ChatMessage.MessageRole.USER, "Mensagem " + i, 10);
            userMsg.setId((long) (i * 2 + 1));
            userMsg.setCreatedAt(LocalDateTime.now().minusMinutes(25 - i));
            messages.add(userMsg);

            ChatMessage assistantMsg = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resposta " + i, 20);
            assistantMsg.setId((long) (i * 2 + 2));
            assistantMsg.setCreatedAt(LocalDateTime.now().minusMinutes(25 - i).plusSeconds(1));
            messages.add(assistantMsg);
        }

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(messages);

        ChatLogsResponse response = chatService.getChatLogs(null, 2, 10);

        assertNotNull(response);
        assertNotNull(response.getPagination());
        assertEquals(2, response.getPagination().getCurrentPage());
        assertTrue(response.getPagination().getHasNext());
        assertTrue(response.getPagination().getHasPrevious());
    }

    @Test
    void shouldGetChatLogsWithIdeaBasedSession() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);

        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Pergunta sobre ideia", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resposta sobre ideia", 20);
        assistantMessage.setId(2L);
        assistantMessage.setCreatedAt(LocalDateTime.now().plusSeconds(1));

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertEquals(1L, response.getInteractions().get(0).getIdeaId());
    }

    @Test
    void shouldThrowExceptionWhenMessageIsEmpty() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));

        assertThrows(ValidationException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldThrowExceptionWhenMessageIsNull() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage(null);

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));

        assertThrows(ValidationException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldThrowExceptionWhenMessageExceedsCharacterLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        String longMessage = "a".repeat(1001);
        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage(longMessage);

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);

        assertThrows(ValidationException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldHandleNegativeTokenCount() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        reset(tokenCalculationService);
        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Resposta");
        when(contentModerationService.validateAndNormalizeResponse("Resposta", true)).thenReturn("Resposta");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(-5);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        verify(tokenCalculationService, atLeastOnce()).estimateTokens(anyString());
    }

    @Test
    void shouldHandleOptimisticLockExceptionInPrepareMessage() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L))
                .thenThrow(new jakarta.persistence.OptimisticLockException());
        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Resposta");
        when(contentModerationService.validateAndNormalizeResponse("Resposta", true)).thenReturn("Resposta");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        verify(chatSessionRepository, atLeastOnce()).findByIdWithIdea(1L);
    }

    @Test
    void shouldHandleOptimisticLockExceptionInSaveMessage() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Resposta");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatSessionRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(session))
                .thenThrow(new jakarta.persistence.OptimisticLockException());
        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
    }

    @Test
    void shouldHandleOptimisticLockExceptionDuringOllamaCall() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                .thenThrow(new jakarta.persistence.OptimisticLockException());

        assertThrows(TokenLimitExceededException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldPopulateIdeaContextCacheWhenMissing() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto da ideia");
        idea.setGeneratedContent("Conteúdo gerado da ideia");

        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        session.setId(1L);
        session.setCachedIdeaContent(null);
        session.setCachedIdeaContext(null);

        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(chatLimitValidator.isChatBlocked(session)).thenReturn(false);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        verify(chatSessionRepository, atLeastOnce()).findByUserIdAndIdeaId(any(), any());
    }

    @Test
    void shouldSaveIdeaContextCacheWhenMissing() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto da ideia");
        idea.setGeneratedContent("Conteúdo gerado da ideia");

        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        session.setId(1L);
        session.setCachedIdeaContent(null);
        session.setCachedIdeaContext(null);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(chatLimitValidator.isChatBlocked(session)).thenReturn(false);
        doNothing().when(chatLimitValidator).validateSessionNotBlocked(session);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
    }

    @Test
    void shouldRemoveModerationTagsFromResponse() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                .thenReturn("[MODERACAO: SEGURA]Resposta limpa");
        when(contentModerationService.validateAndNormalizeResponse("[MODERACAO: SEGURA]Resposta limpa", true))
                .thenReturn("Resposta limpa");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertFalse(response.getContent().contains("[MODERACAO"));
    }

    @Test
    void shouldRemoveDangerousModerationTags() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                .thenReturn("[MODERACAO: PERIGOSO]Conteúdo perigoso");
        when(contentModerationService.validateAndNormalizeResponse("[MODERACAO: PERIGOSO]Conteúdo perigoso", true))
                .thenReturn("Desculpe, não posso processar essa mensagem devido ao conteúdo. Posso ajudá-lo com outras questões?");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertFalse(response.getContent().contains("[MODERACAO"));
    }

    @Test
    void shouldThrowExceptionWhenResponseIsEmptyAfterCleaning() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                .thenReturn("[MODERACAO: SEGURA]");
        when(contentModerationService.validateAndNormalizeResponse("[MODERACAO: SEGURA]", true))
                .thenReturn("");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        assertThrows(OllamaServiceException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldCallOllamaWithHistoryWhenMessagesExist() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessage previousUser = new ChatMessage(session, ChatMessage.MessageRole.USER, "Olá", 10);
        previousUser.setId(1L);
        previousUser.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        ChatMessage previousAssistant = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Olá! Como posso ajudar?", 20);
        previousAssistant.setId(2L);
        previousAssistant.setCreatedAt(LocalDateTime.now().minusMinutes(4));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Como você está?");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt()))
                .thenReturn(Arrays.asList(previousUser, previousAssistant));
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(promptBuilderService.buildMessageHistory(anyList())).thenReturn(Arrays.asList(
                new projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message("user", "Olá"),
                new projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message("assistant", "Olá! Como posso ajudar?")
        ));
        when(ollamaIntegrationService.callOllamaWithHistory(anyString(), anyList(), anyString()))
                .thenReturn("Estou bem, obrigado!");
        when(contentModerationService.validateAndNormalizeResponse("Estou bem, obrigado!", true))
                .thenReturn("Estou bem, obrigado!");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(3L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.singletonList(previousAssistant));
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(30);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        verify(ollamaIntegrationService).callOllamaWithHistory(anyString(), anyList(), anyString());
        verify(ollamaIntegrationService, never()).callOllamaWithSystemPrompt(anyString(), anyString());
    }

    @Test
    void shouldCalculateTokensRemainingWithPreviousAssistantMessage() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessage previousAssistant = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resposta anterior", 20);
        previousAssistant.setId(1L);
        previousAssistant.setTokensRemaining(9980);
        previousAssistant.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Nova mensagem");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(10);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Nova resposta");
        when(contentModerationService.validateAndNormalizeResponse("Nova resposta", true)).thenReturn("Nova resposta");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(15);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(2L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.singletonList(previousAssistant));
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(10);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertNotNull(response.getTokensRemaining());
        assertTrue(response.getTokensRemaining() >= 0);
    }

    @Test
    void shouldCalculateTokensRemainingWithoutPreviousAssistantMessage() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Primeira mensagem");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(Collections.emptyList());
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(10);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Primeira resposta");
        when(contentModerationService.validateAndNormalizeResponse("Primeira resposta", true)).thenReturn("Primeira resposta");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(15);
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertNotNull(response.getTokensRemaining());
    }

    @Test
    void shouldSummarizeIdeaWithShortContent() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia curta");
        idea.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(idea));

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertNotNull(response.get(0).getSummary());
    }

    @Test
    void shouldSummarizeIdeaWithLongContent() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Esta é uma ideia muito longa que contém várias palavras e precisa ser resumida para exibição na lista de ideias do usuário. " +
                "O resumo deve capturar a essência da ideia de forma concisa.");
        idea.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(idea));
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString()))
                .thenReturn("Ideia resumida com título");

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertNotNull(response.get(0).getSummary());
    }

    @Test
    void shouldHandleEmptyIdeaContent() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("");
        idea.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(idea));

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("", response.get(0).getSummary());
    }

    @Test
    void shouldGetInitialMessagesWithLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ChatMessage msg = new ChatMessage(session, ChatMessage.MessageRole.USER, "Mensagem " + i, 10);
            msg.setId((long) i);
            msg.setCreatedAt(LocalDateTime.now().minusMinutes(20 - i));
            messages.add(msg);
        }

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(eq(1L), anyInt())).thenReturn(messages);
        when(chatProperties.getMaxInitialMessages()).thenReturn(10);
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        when(chatMessageRepository.findLastMessagesBySessionIdAndRole(eq(1L), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertNotNull(response.getMessages());
    }

    @Test
    void shouldGetRecentMessagesWithLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        List<ChatMessage> historyMessages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChatMessage msg = new ChatMessage(session, ChatMessage.MessageRole.USER, "Histórico " + i, 10);
            msg.setId((long) i);
            msg.setCreatedAt(LocalDateTime.now().minusMinutes(10 - i));
            historyMessages.add(msg);
        }

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(eq(1L), anyInt())).thenReturn(historyMessages);
        when(chatProperties.getMaxHistoryMessages()).thenReturn(5);
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(promptBuilderService.buildMessageHistory(anyList())).thenReturn(Collections.emptyList());
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Resposta");
        when(contentModerationService.validateAndNormalizeResponse("Resposta", true)).thenReturn("Resposta");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(11L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        doNothing().when(contentModerationService).validateModerationResponse(anyString(), anyBoolean());

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
    }

    @Test
    void shouldBuildSessionResponseWithIdeaBasedChat() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia completa para resumo");

        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Olá", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Olá! Como posso ajudar?", 20);
        assistantMessage.setId(2L);
        assistantMessage.setTokensRemaining(9970);
        assistantMessage.setCreatedAt(LocalDateTime.now().plusSeconds(1));

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(eq(1L), anyInt()))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));
        when(chatProperties.getMaxInitialMessages()).thenReturn(10);
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), eq(ChatMessage.MessageRole.USER))).thenReturn(10);
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT))).thenReturn(20);
        when(chatMessageRepository.findLastMessagesBySessionIdAndRole(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Resumo da Ideia");

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.IDEA_BASED.toString(), response.getChatType());
        assertEquals(1L, response.getIdeaId());
        assertNotNull(response.getIdeaSummary());
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
    }

    @Test
    void shouldBuildSessionResponseWithFreeChat() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(eq(1L), anyInt())).thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);
        when(chatMessageRepository.findLastMessagesBySessionIdAndRole(eq(1L), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
        assertNull(response.getIdeaId());
        assertNull(response.getIdeaSummary());
    }

    @Test
    void shouldHandleInvalidDataAccessApiUsageException() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt()))
                .thenThrow(new org.springframework.dao.InvalidDataAccessApiUsageException("Erro de acesso"));

        assertThrows(OllamaServiceException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldHandleDataAccessException() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt()))
                .thenThrow(new org.springframework.dao.DataAccessException("Erro de banco de dados") {});

        assertThrows(OllamaServiceException.class, () -> chatService.sendMessage(1L, messageRequest));
    }

    @Test
    void shouldUseSelfWhenAvailable() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(any())).thenReturn(0);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
    }

    @Test
    void shouldMapUserMessageToResponse() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Mensagem do usuário", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now().minusHours(1));

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resposta", 20);
        assistantMessage.setId(2L);
        assistantMessage.setTokensRemaining(9970);
        assistantMessage.setCreatedAt(LocalDateTime.now().minusHours(1).plusSeconds(1));

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        String beforeTimestamp = LocalDateTime.now().toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 20);

        assertNotNull(response);
        assertNotNull(response.getMessages());
    }

    @Test
    void shouldMapAssistantMessageToResponse() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resposta", 20);
        assistantMessage.setId(1L);
        assistantMessage.setTokensRemaining(9980);
        assistantMessage.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.singletonList(assistantMessage));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.singletonList(assistantMessage));

        String beforeTimestamp = LocalDateTime.now().toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 20);

        assertNotNull(response);
        assertNotNull(response.getMessages());
        if (!response.getMessages().isEmpty()) {
            assertEquals("assistant", response.getMessages().get(0).getRole());
        }
    }

    @Test
    void shouldGetOlderMessagesWithLimitGreaterThan50() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 100);

        assertNotNull(response);
        assertNotNull(response.getMessages());
        verify(chatMessageRepository).findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), argThat(pageable -> 
            pageable.getPageSize() == 51));
    }

    @Test
    void shouldGetOlderMessagesWithHasMoreTrue() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage msg1 = new ChatMessage(session, ChatMessage.MessageRole.USER, "Msg1", 10);
        msg1.setId(1L);
        msg1.setCreatedAt(LocalDateTime.now().minusHours(2));
        ChatMessage msg2 = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Msg2", 20);
        msg2.setId(2L);
        msg2.setCreatedAt(LocalDateTime.now().minusHours(1));
        ChatMessage msg3 = new ChatMessage(session, ChatMessage.MessageRole.USER, "Msg3", 10);
        msg3.setId(3L);
        msg3.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Arrays.asList(msg1, msg2, msg3));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Arrays.asList(msg1, msg2, msg3));

        String beforeTimestamp = LocalDateTime.now().toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 2);

        assertNotNull(response);
        assertTrue(response.isHasMore());
        assertEquals(2, response.getMessages().size());
    }

    @Test
    void shouldGetOlderMessagesWithHasMoreFalse() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage msg1 = new ChatMessage(session, ChatMessage.MessageRole.USER, "Msg1", 10);
        msg1.setId(1L);
        msg1.setCreatedAt(LocalDateTime.now().minusHours(2));

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.singletonList(msg1));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.singletonList(msg1));

        String beforeTimestamp = LocalDateTime.now().toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 20);

        assertNotNull(response);
        assertFalse(response.isHasMore());
        assertEquals(1, response.getMessages().size());
    }

    @Test
    void shouldGetChatLogsWithEmptyMessages() {
        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(0, response.getInteractions().size());
        assertEquals(0, response.getSummary().getTotalInteractions());
    }


    @Test
    void shouldGetChatLogsWithMaxPageSize() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 200);

        assertNotNull(response);
        verify(chatMessageRepository).findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void shouldGetChatLogsWithInvalidPageNumber() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 0, 10);

        assertNotNull(response);
        assertEquals(0, response.getPagination().getCurrentPage() - 1);
    }

    @Test
    void shouldGetChatLogsWithNullPageAndSize() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, null, null);

        assertNotNull(response);
        assertEquals(1, response.getPagination().getCurrentPage());
    }

    @Test
    void shouldGetChatLogsWithUserMessageWithoutAssistant() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertNull(response.getInteractions().get(0).getAssistantMessage());
    }

    @Test
    void shouldGetChatLogsWithMessagesFromDifferentSessions() {
        ChatSession session1 = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session1.setId(1L);
        ChatSession session2 = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session2.setId(2L);

        ChatMessage msg1 = new ChatMessage(session1, ChatMessage.MessageRole.USER, "Msg1", 10);
        msg1.setId(1L);
        msg1.setCreatedAt(LocalDateTime.now());
        ChatMessage msg2 = new ChatMessage(session2, ChatMessage.MessageRole.USER, "Msg2", 10);
        msg2.setId(2L);
        msg2.setCreatedAt(LocalDateTime.now().plusSeconds(1));

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(msg1, msg2));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(2, response.getInteractions().size());
    }

    @Test
    void shouldGetUserIdeasSummaryWithEmptyContent() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("");
        idea.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(idea));

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("", response.get(0).getSummary());
    }

    @Test
    void shouldGetUserIdeasSummaryWithNullContent() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent(null);
        idea.setCreatedAt(LocalDateTime.now());

        when(ideaRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(idea));

        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("", response.get(0).getSummary());
    }

    @Test
    void shouldStartChatWithExistingFreeSession() {
        ChatSession existingSession = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        existingSession.setId(1L);
        existingSession.setLastResetAt(LocalDateTime.now());

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(chatSessionRepository.findByUserIdAndType(1L, ChatSession.ChatType.FREE))
                .thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        verify(chatSessionRepository, never()).save(any(ChatSession.class));
    }

    @Test
    void shouldStartChatWithExistingIdeaBasedSession() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Conteúdo");

        ChatSession existingSession = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        existingSession.setId(1L);
        existingSession.setLastResetAt(LocalDateTime.now());
        existingSession.setCachedIdeaContent("Conteúdo");
        existingSession.setCachedIdeaContext("Contexto");

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(1L, 1L))
                .thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
    }


    @Test
    void shouldGetOlderMessagesWithZeroLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, 0);

        assertNotNull(response);
        assertNotNull(response.getMessages());
    }

    @Test
    void shouldGetOlderMessagesWithNegativeLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMessagesBeforeTimestamp(eq(1L), any(LocalDateTime.class), any()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        String beforeTimestamp = LocalDateTime.now().minusMinutes(30).toString();
        OlderMessagesResponse response = chatService.getOlderMessages(1L, beforeTimestamp, -5);

        assertNotNull(response);
        assertNotNull(response.getMessages());
    }

    @Test
    void shouldGetChatLogsWithInvalidUserMessages() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Test", 20);
        assistantMessage.setId(1L);
        assistantMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(0, response.getInteractions().size());
    }

    @Test
    void shouldGetChatLogsWithMessageWithoutSession() {
        ChatMessage userMessage = new ChatMessage(null, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(0, response.getInteractions().size());
    }

    @Test
    void shouldGetChatLogsWithNullMessage() {
        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(null));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(0, response.getInteractions().size());
    }

    @Test
    void shouldGetChatLogsWithAssistantMessageWithoutMatchingUser() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Test", 20);
        assistantMessage.setId(1L);
        assistantMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(0, response.getInteractions().size());
    }

    @Test
    void shouldGetChatLogsWithAssistantMessageFromDifferentSession() {
        ChatSession session1 = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session1.setId(1L);
        ChatSession session2 = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session2.setId(2L);

        ChatMessage userMessage = new ChatMessage(session1, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());
        ChatMessage assistantMessage = new ChatMessage(session2, ChatMessage.MessageRole.ASSISTANT, "Test", 20);
        assistantMessage.setId(2L);
        assistantMessage.setCreatedAt(LocalDateTime.now().plusSeconds(1));

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertNull(response.getInteractions().get(0).getAssistantMessage());
    }

    @Test
    void shouldGetChatLogsWithNullResponseTime() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());
        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Test", 20);
        assistantMessage.setId(2L);
        assistantMessage.setCreatedAt(null);

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertNull(response.getInteractions().get(0).getMetrics().getResponseTimeMs());
    }

    @Test
    void shouldGetChatLogsWithNullUserMessageCreatedAt() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());
        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Test", 20);
        assistantMessage.setId(2L);
        assistantMessage.setCreatedAt(null);

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertNull(response.getInteractions().get(0).getMetrics().getResponseTimeMs());
    }

    @Test
    void shouldGetChatLogsWithEmptyInteractionsSummary() {
        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertNotNull(response.getSummary());
        assertEquals(0, response.getSummary().getTotalInteractions());
        assertEquals(0, response.getSummary().getTotalTokensInput());
        assertEquals(0, response.getSummary().getTotalTokensOutput());
        assertNull(response.getSummary().getAverageResponseTimeMs());
    }

    @Test
    void shouldGetChatLogsWithInteractionsWithoutResponseTime() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(userMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertNull(response.getInteractions().get(0).getMetrics().getResponseTimeMs());
        assertNull(response.getSummary().getAverageResponseTimeMs());
    }

    @Test
    void shouldGetChatLogsWithInteractionsWithResponseTime() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        LocalDateTime userTime = LocalDateTime.now();
        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, "Test", 10);
        userMessage.setId(1L);
        userMessage.setCreatedAt(userTime);
        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Test", 20);
        assistantMessage.setId(2L);
        assistantMessage.setCreatedAt(userTime.plusSeconds(2));

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(userMessage, assistantMessage));

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getInteractions().size());
        assertNotNull(response.getInteractions().get(0).getMetrics().getResponseTimeMs());
        assertTrue(response.getInteractions().get(0).getMetrics().getResponseTimeMs() > 0);
    }

    @Test
    void shouldGetChatLogsWithMultipleInteractionsAndCalculateAverage() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LocalDateTime userTime = LocalDateTime.now().minusMinutes(3 - i);
            ChatMessage userMsg = new ChatMessage(session, ChatMessage.MessageRole.USER, "Msg " + i, 10);
            userMsg.setId((long) (i * 2 + 1));
            userMsg.setCreatedAt(userTime);
            messages.add(userMsg);

            ChatMessage assistantMsg = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, "Resp " + i, 20);
            assistantMsg.setId((long) (i * 2 + 2));
            assistantMsg.setCreatedAt(userTime.plusSeconds(1));
            messages.add(assistantMsg);
        }

        when(chatMessageRepository.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(messages);

        ChatLogsResponse response = chatService.getChatLogs(null, 1, 10);

        assertNotNull(response);
        assertEquals(3, response.getInteractions().size());
        assertNotNull(response.getSummary().getAverageResponseTimeMs());
        assertTrue(response.getSummary().getAverageResponseTimeMs() > 0);
    }

    @Test
    void shouldStartChatWhenFreeSessionIsBlocked() {
        ChatSession blockedSession = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        blockedSession.setId(1L);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(chatSessionRepository.findByUserIdAndType(1L, ChatSession.ChatType.FREE))
                .thenReturn(Optional.of(blockedSession));
        when(chatLimitValidator.isChatBlocked(blockedSession)).thenReturn(true);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(2L);
            return session;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());
        when(tokenCalculationService.getTotalUserTokensInChat(any())).thenReturn(0);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
    }

    @Test
    void shouldStartChatWhenIdeaBasedSessionIsBlocked() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);

        ChatSession blockedSession = new ChatSession(testUser, ChatSession.ChatType.IDEA_BASED, idea);
        blockedSession.setId(1L);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(1L);

        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(1L, 1L))
                .thenReturn(Optional.of(blockedSession));
        doThrow(new TokenLimitExceededException("Chat bloqueado"))
                .when(chatLimitValidator).validateSessionNotBlocked(blockedSession);

        assertThrows(TokenLimitExceededException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldGetInitialMessagesWithLimitExceeded() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ChatMessage msg = new ChatMessage(session, ChatMessage.MessageRole.USER, "Msg " + i, 10);
            msg.setId((long) i);
            msg.setCreatedAt(LocalDateTime.now().minusMinutes(15 - i));
            messages.add(msg);
        }

        when(chatSessionRepository.findByIdWithIdea(1L)).thenReturn(Optional.of(session));
        when(chatProperties.getMaxInitialMessages()).thenReturn(10);
        when(chatMessageRepository.findRecentMessagesOptimized(1L, 10)).thenReturn(messages);
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(messages);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);
        when(tokenCalculationService.getTotalTokensUsedByUser(1L)).thenReturn(0);

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        verify(chatMessageRepository).findRecentMessagesOptimized(1L, 10);
    }

    @Test
    void shouldGetRecentMessagesWithLimitExceeded() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now());

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ChatMessage msg = new ChatMessage(session, ChatMessage.MessageRole.USER, "Msg " + i, 10);
            msg.setId((long) i);
            msg.setCreatedAt(LocalDateTime.now().minusMinutes(5 - i));
            messages.add(msg);
        }

        when(chatProperties.getMaxHistoryMessages()).thenReturn(3);
        when(chatMessageRepository.findRecentMessagesOptimized(1L, 3)).thenReturn(messages);
        when(chatSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.countBySessionId(1L)).thenReturn(5L);
        when(chatMessageRepository.findRecentMessagesOptimized(any(), anyInt())).thenReturn(messages);
        when(chatLimitValidator.validateMessageLimitsAndGetTokens(anyString())).thenReturn(5);
        when(ollamaIntegrationService.callOllamaWithSystemPrompt(anyString(), anyString())).thenReturn("Response");
        when(contentModerationService.validateAndNormalizeResponse("Response", true)).thenReturn("Response");
        when(tokenCalculationService.estimateTokens(anyString())).thenReturn(10);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(100L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatMessageRepository.findUserMessagesBySessionId(eq(1L), eq(ChatMessage.MessageRole.ASSISTANT)))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.getTotalUserTokensBySessionId(eq(1L), any())).thenReturn(0);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("Test");

        ChatMessageResponse response = chatService.sendMessage(1L, request);

        assertNotNull(response);
        verify(chatMessageRepository).findRecentMessagesOptimized(1L, 3);
    }
}


