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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import projeto_gerador_ideias_backend.dto.ChatMessageRequest;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.dto.StartChatRequest;
import projeto_gerador_ideias_backend.model.ChatSession;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.ChatMessageRepository;
import projeto_gerador_ideias_backend.repository.ChatSessionRepository;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

    public static MockWebServer mockWebServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private projeto_gerador_ideias_backend.service.UserCacheService userCacheService;

    private final String testUserEmail = "chat-controller@example.com";
    private User testUser;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        if (mockWebServer == null) {
            try {
                mockWebServer = new MockWebServer();
                mockWebServer.start();
            } catch (IOException e) {
                throw new RuntimeException("Falha ao iniciar MockWebServer", e);
            }
        }
        registry.add("ollama.base-url", () -> mockWebServer.url("/").toString());
    }

    @BeforeAll
    static void setUp() throws IOException {
        if (mockWebServer == null) {
            mockWebServer = new MockWebServer();
            mockWebServer.start();
        }
    }

    @BeforeEach
    void setUpDatabase() {
        if (userCacheService != null) {
            userCacheService.invalidateAllCache();
            userCacheService.clearRequestCache();
        }
        
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        ideaRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setEmail(testUserEmail);
        testUser.setName("Chat Controller User");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser = userRepository.save(testUser);
        userRepository.flush();
    }

    @AfterEach
    void tearDownDatabase() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        ideaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
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
    @WithMockUser(username = "chat-controller@example.com")
    void shouldStartFreeChatSuccessfully() throws Exception {
        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(null);

        mockMvc.perform(post("/api/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatType").value("FREE"))
                .andExpect(jsonPath("$.ideaId").isEmpty())
                .andExpect(jsonPath("$.totalTokens").value(0))
                .andExpect(jsonPath("$.tokensRemaining").value(10000));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldStartIdeaBasedChatSuccessfully() throws Exception {
        Idea idea = new Idea();
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto de teste");
        idea.setGeneratedContent("Ideia de teste");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        idea = ideaRepository.save(idea);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(idea.getId());

        mockMvc.perform(post("/api/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatType").value("IDEA_BASED"))
                .andExpect(jsonPath("$.ideaId").value(idea.getId().intValue()))
                .andExpect(jsonPath("$.totalTokens").value(0))
                .andExpect(jsonPath("$.tokensRemaining").value(10000));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetSessionSuccessfully() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        mockMvc.perform(get("/api/chat/sessions/" + session.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId().intValue()))
                .andExpect(jsonPath("$.chatType").value("FREE"))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetUserIdeasSummarySuccessfully() throws Exception {
        Idea idea = new Idea();
        idea.setUser(testUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia de teste para resumo");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        idea = ideaRepository.save(idea);
        ideaRepository.flush();

        mockMvc.perform(get("/api/chat/ideas/summary"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].summary").exists());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn404WhenSessionNotFound() throws Exception {
        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        mockMvc.perform(post("/api/chat/sessions/99999/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldValidateMessageSize() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("x".repeat(1001));

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn404WhenIdeaNotFound() throws Exception {
        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(99999L);

        mockMvc.perform(post("/api/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn403WhenIdeaBelongsToAnotherUser() throws Exception {
        User otherUser = new User();
        otherUser.setEmail("other@example.com");
        otherUser.setName("Other User");
        otherUser.setPassword(passwordEncoder.encode("password"));
        otherUser = userRepository.save(otherUser);

        Idea idea = new Idea();
        idea.setUser(otherUser);
        idea.setTheme(Theme.TECNOLOGIA);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        idea = ideaRepository.save(idea);

        StartChatRequest request = new StartChatRequest();
        request.setIdeaId(idea.getId());

        mockMvc.perform(post("/api/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn404WhenGettingNonExistentSession() throws Exception {
        mockMvc.perform(get("/api/chat/sessions/99999"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn400WhenSendingDangerousMessage() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse("[MODERACAO: PERIGOSO]"))
                .addHeader("Content-Type", "application/json"));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Conteúdo perigoso");

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn400WhenTokenLimitExceeded() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        String largeMessage = "word ".repeat(1875);
        for (int i = 0; i < 4; i++) {
            projeto_gerador_ideias_backend.model.ChatMessage msg = 
                new projeto_gerador_ideias_backend.model.ChatMessage(
                    session,
                    projeto_gerador_ideias_backend.model.ChatMessage.MessageRole.USER,
                    largeMessage,
                    2500
                );
            chatMessageRepository.save(msg);
        }

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste");

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}

