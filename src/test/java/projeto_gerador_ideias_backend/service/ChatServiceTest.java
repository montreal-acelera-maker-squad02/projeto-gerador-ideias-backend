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
    void shouldSendMessageInFreeChatSuccessfully() throws Exception {
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
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class)); // User + Assistant
    }

    @Test
    void shouldThrowExceptionWhenSessionNotFound() {
        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        when(chatSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> chatService.sendMessage(999L, messageRequest));
    }

    @Test
    void shouldThrowExceptionWhenMessageIsDangerous() throws Exception {
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
}

