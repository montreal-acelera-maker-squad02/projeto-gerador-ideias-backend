package projeto_gerador_ideias_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import projeto_gerador_ideias_backend.dto.request.ChatMessageRequest;
import projeto_gerador_ideias_backend.dto.response.OllamaResponse;
import projeto_gerador_ideias_backend.dto.request.StartChatRequest;
import projeto_gerador_ideias_backend.model.ChatSession;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.*;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
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

    @Autowired
    private ThemeRepository themeRepository;

    private final String testUserEmail = "chat-controller@example.com";
    private User testUser;
    private Theme tecnologiaTheme;

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
        themeRepository.deleteAll();

        testUser = new User();
        testUser.setEmail(testUserEmail);
        testUser.setName("Chat Controller User");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser = userRepository.save(testUser);
        userRepository.flush();

        tecnologiaTheme = new Theme();
        tecnologiaTheme.setName("TECNOLOGIA");
        tecnologiaTheme = themeRepository.save(tecnologiaTheme);
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
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto de teste");
        idea.setGeneratedContent("Ideia de teste");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        ideaRepository.save(idea);

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
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia de teste para resumo");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        ideaRepository.save(idea);
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
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto");
        idea.setGeneratedContent("Ideia");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        ideaRepository.save(idea);

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

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn400WhenMessageIsNull() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage(null);

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("não pode estar vazia")));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldReturn400WhenMessageExceeds1000Characters() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("x".repeat(1001));

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("não pode ter mais de 1000 caracteres")));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetChatLogsWithDate() throws Exception {
        mockMvc.perform(get("/api/chat/logs")
                        .param("date", "2025-11-08"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetChatLogsWithPagination() throws Exception {
        mockMvc.perform(get("/api/chat/logs")
                        .param("page", "2")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetChatLogsWithDefaultParameters() throws Exception {
        mockMvc.perform(get("/api/chat/logs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetOlderMessages() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        mockMvc.perform(get("/api/chat/sessions/" + session.getId() + "/messages")
                        .param("before", "2025-11-08T13:00:00")
                        .param("limit", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetOlderMessagesWithDefaultLimit() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        mockMvc.perform(get("/api/chat/sessions/" + session.getId() + "/messages")
                        .param("before", "2025-11-08T13:00:00"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldSendMessageEndToEnd() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        String aiResponse = "Olá! Como posso ajudar você hoje?";
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(aiResponse))
                .addHeader("Content-Type", "application/json"));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Olá, tudo bem?");

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content").value(aiResponse));

        chatMessageRepository.flush();
        long messageCount = chatMessageRepository.count();
        assertEquals(2L, messageCount);
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldCompleteFlowStartChatSendMessageGetSession() throws Exception {
        StartChatRequest startRequest = new StartChatRequest();
        startRequest.setIdeaId(null);

        String startResponse = mockMvc.perform(post("/api/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatType").value("FREE"))
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long sessionId = objectMapper.readTree(startResponse).get("sessionId").asLong();

        String aiResponse = "Esta é uma resposta de teste.";
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(aiResponse))
                .addHeader("Content-Type", "application/json"));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Mensagem de teste");

        mockMvc.perform(post("/api/chat/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(aiResponse));

        mockMvc.perform(get("/api/chat/sessions/" + sessionId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.intValue()))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldSendMultipleMessagesAndMaintainHistory() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        String[] aiResponses = {
            "Primeira resposta",
            "Segunda resposta",
            "Terceira resposta"
        };

        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setBody(createMockOllamaResponse(aiResponses[i]))
                    .addHeader("Content-Type", "application/json"));

            ChatMessageRequest messageRequest = new ChatMessageRequest();
            messageRequest.setMessage("Mensagem " + (i + 1));

            mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(messageRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value(aiResponses[i]));
        }

        chatMessageRepository.flush();
        long totalMessages = chatMessageRepository.count();
        assertEquals(6L, totalMessages);

        mockMvc.perform(get("/api/chat/sessions/" + session.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(6));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetChatLogsWithRealMessages() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        String aiResponse = "Resposta para logs";
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(aiResponse))
                .addHeader("Content-Type", "application/json"));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Mensagem para logs");

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andExpect(status().isOk());

        chatMessageRepository.flush();

        String today = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);

        mockMvc.perform(get("/api/chat/logs")
                        .param("date", today))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDate").value(today))
                .andExpect(jsonPath("$.interactions").isArray())
                .andExpect(jsonPath("$.interactions.length()").value(1))
                .andExpect(jsonPath("$.interactions[0].userMessage").exists())
                .andExpect(jsonPath("$.interactions[0].assistantMessage").exists())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.pagination").exists());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldGetOlderMessagesWithRealData() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(10);

        for (int i = 0; i < 5; i++) {
            projeto_gerador_ideias_backend.model.ChatMessage userMsg = 
                new projeto_gerador_ideias_backend.model.ChatMessage(
                    session,
                    projeto_gerador_ideias_backend.model.ChatMessage.MessageRole.USER,
                    "Mensagem antiga " + i,
                    10
                );
            userMsg.setCreatedAt(baseTime.plusMinutes(i));
            chatMessageRepository.saveAndFlush(userMsg);

            projeto_gerador_ideias_backend.model.ChatMessage assistantMsg = 
                new projeto_gerador_ideias_backend.model.ChatMessage(
                    session,
                    projeto_gerador_ideias_backend.model.ChatMessage.MessageRole.ASSISTANT,
                    "Resposta " + i,
                    20
                );
            assistantMsg.setCreatedAt(baseTime.plusMinutes(i).plusSeconds(1));
            chatMessageRepository.saveAndFlush(assistantMsg);
        }

        LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(5);

        mockMvc.perform(get("/api/chat/sessions/" + session.getId() + "/messages")
                        .param("before", beforeTime.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("limit", "4"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldCalculateTokensCorrectlyAfterMultipleMessages() throws Exception {
        ChatSession session = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session.setLastResetAt(LocalDateTime.now());
        session = chatSessionRepository.save(session);

        String aiResponse = "Resposta curta";
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(aiResponse))
                .addHeader("Content-Type", "application/json"));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Teste de tokens");

        mockMvc.perform(post("/api/chat/sessions/" + session.getId() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andExpect(status().isOk());

        chatMessageRepository.flush();

        mockMvc.perform(get("/api/chat/sessions/" + session.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens").exists())
                .andExpect(jsonPath("$.tokensRemaining").exists())
                .andExpect(jsonPath("$.tokensRemaining").value(org.hamcrest.Matchers.lessThan(10000)));
    }

    @Test
    @WithMockUser(username = "chat-controller@example.com")
    void shouldCompleteFlowIdeaBasedChatEndToEnd() throws Exception {
        Idea idea = new Idea();
        idea.setUser(testUser);
        idea.setTheme(tecnologiaTheme);
        idea.setContext("Contexto de teste para chat");
        idea.setGeneratedContent("Ideia de teste para chat baseado em ideia");
        idea.setModelUsed("mistral");
        idea.setExecutionTimeMs(1000L);
        idea = ideaRepository.save(idea);

        StartChatRequest startRequest = new StartChatRequest();
        startRequest.setIdeaId(idea.getId());

        String startResponse = mockMvc.perform(post("/api/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatType").value("IDEA_BASED"))
                .andExpect(jsonPath("$.ideaId").value(idea.getId().intValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long sessionId = objectMapper.readTree(startResponse).get("sessionId").asLong();

        String aiResponse = "Resposta relacionada à ideia de tecnologia";
        mockWebServer.enqueue(new MockResponse()
                .setBody(createMockOllamaResponse(aiResponse))
                .addHeader("Content-Type", "application/json"));

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Como posso implementar essa ideia?");

        mockMvc.perform(post("/api/chat/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(aiResponse));

        chatMessageRepository.flush();

        mockMvc.perform(get("/api/chat/sessions/" + sessionId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatType").value("IDEA_BASED"))
                .andExpect(jsonPath("$.ideaId").value(idea.getId().intValue()))
                .andExpect(jsonPath("$.ideaSummary").exists())
                .andExpect(jsonPath("$.messages.length()").value(2));
    }
}


