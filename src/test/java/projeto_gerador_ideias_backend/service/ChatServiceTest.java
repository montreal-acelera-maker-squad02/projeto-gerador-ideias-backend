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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.*;
import projeto_gerador_ideias_backend.exceptions.ChatPermissionException;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.TokenLimitExceededException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.model.*;
import projeto_gerador_ideias_backend.repository.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private final String testUserEmail = "chat-service@example.com";

    @BeforeEach
    void setUpEach() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient.Builder webClientBuilder = WebClient.builder();

        chatService = new ChatService(
                chatSessionRepository,
                chatMessageRepository,
                ideaRepository,
                userRepository,
                webClientBuilder,
                baseUrl
        );
        ReflectionTestUtils.setField(chatService, "ollamaModel", "mistral-test");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(testUserEmail);
        testUser.setName("Chat Service User");

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(1L);
            return session;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
        assertEquals(0, response.getTokensUsed());
        assertEquals(1000, response.getTokensRemaining());
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(1L);
            return session;
        });
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(Collections.emptyList());

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

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

        when(chatSessionRepository.findById(999L)).thenReturn(Optional.empty());

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
    }

    @Test
    void shouldThrowExceptionWhenGettingNonExistentSession() {
        when(chatSessionRepository.findById(999L)).thenReturn(Optional.empty());

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(existingSession));

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.FREE.toString(), response.getChatType());
        verify(chatSessionRepository, never()).save(any(ChatSession.class));
    }

    @Test
    void shouldResetTokensWhen24HoursPassed() {
        ChatSession oldSession = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        oldSession.setId(1L);
        oldSession.setLastResetAt(LocalDateTime.now().minusHours(25));
        oldSession.setTokensUsed(1000);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.of(oldSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(oldSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(oldSession);

        ChatSessionResponse response = chatService.startChat(request);

        assertNotNull(response);
        verify(chatSessionRepository).save(argThat(session -> session.getTokensUsed() == 0));
    }

    @Test
    void shouldThrowExceptionWhenFreeChatReachesTokenLimit() {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now().minusHours(10));
        session.setTokensUsed(1000);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.of(session));

        assertThrows(TokenLimitExceededException.class, () -> chatService.startChat(request));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(existingSession));

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.empty());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(blockedSession));

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(chatSessionRepository.findByUserIdAndIdeaId(any(), any())).thenReturn(Optional.of(existingSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(existingSession));

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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertEquals("assistant", response.getRole());
        assertEquals(1, mockWebServer.getRequestCount());
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.singletonList(previousMessage));
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(2L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        ChatMessageResponse response = chatService.sendMessage(1L, messageRequest);

        assertNotNull(response);
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void shouldResetTokensWhen24HoursPassedInSendMessage() throws JsonProcessingException {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setId(1L);
        session.setLastResetAt(LocalDateTime.now().minusHours(25));
        session.setTokensUsed(1000);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("SEGURO"))
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("Resposta"))
                .addHeader("Content-Type", "application/json"));

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession saved = invocation.getArgument(0);
            if (saved.getTokensUsed() == 0) {
                saved.setLastResetAt(LocalDateTime.now());
            }
            return saved;
        });
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        chatService.sendMessage(1L, messageRequest);

        verify(chatSessionRepository, atLeastOnce()).save(argThat(s -> s.getTokensUsed() == 0 || s.getTokensUsed() < 1000));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList(session, otherSession));

        assertThrows(TokenLimitExceededException.class, () -> chatService.sendMessage(1L, messageRequest));
    }


    @Test
    void shouldHandleOllamaConnectionRefused() throws IOException {
        try (MockWebServer disconnectedServer = new MockWebServer()) {
            disconnectedServer.start();
            int port = disconnectedServer.getPort();
            disconnectedServer.shutdown();

            String disconnectedUrl = String.format("http://localhost:%s", port);
            WebClient.Builder webClientBuilder = WebClient.builder();
            ChatService disconnectedService = new ChatService(
                    chatSessionRepository,
                    chatMessageRepository,
                    ideaRepository,
                    userRepository,
                    webClientBuilder,
                    disconnectedUrl
            );
            ReflectionTestUtils.setField(disconnectedService, "ollamaModel", "mistral-test");

            ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
            session.setId(1L);
            session.setLastResetAt(LocalDateTime.now());

            ChatMessageRequest messageRequest = new ChatMessageRequest();
            messageRequest.setMessage("Teste");

            when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
            when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
            when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                disconnectedService.sendMessage(1L, messageRequest);
            });

            assertTrue(exception.getMessage().contains("conectar") || exception.getMessage().contains("Connection") || 
                       exception.getMessage().contains("Ollama"));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            chatService.sendMessage(1L, messageRequest);
        });

        assertTrue(exception.getMessage().contains("Erro") || exception.getMessage().contains("Erro ao processar mensagem"));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.singletonList(message));
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.singletonList(session));

        ChatSessionResponse response = chatService.getSession(1L);

        assertNotNull(response);
        assertEquals(1L, response.getSessionId());
        assertEquals(ChatSession.ChatType.IDEA_BASED.toString(), response.getChatType());
        assertEquals(1L, response.getIdeaId());
        assertNotNull(response.getIdeaSummary());
        assertEquals(1, response.getMessages().size());
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
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

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.of(testUser));
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(ChatPermissionException.class, () -> chatService.getSession(1L));
    }

    @Test
    void shouldThrowExceptionWhenUserNotAuthenticated() {
        SecurityContextHolder.clearContext();

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        assertThrows(ResourceNotFoundException.class, () -> chatService.startChat(request));
    }

    @Test
    void shouldThrowExceptionWhenAuthenticatedUserNotFound() {
        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.empty());

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        assertThrows(ResourceNotFoundException.class, () -> chatService.startChat(request));
    }
}

