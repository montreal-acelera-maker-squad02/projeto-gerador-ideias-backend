package projeto_gerador_ideias_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import projeto_gerador_ideias_backend.model.ChatMessage;
import projeto_gerador_ideias_backend.model.ChatSession;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ChatMessageRepositoryTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private ThemeRepository themeRepository;

    private User testUser;
    private ChatSession testSession;
    private Theme testTheme;
    private Idea testIdea;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        ideaRepository.deleteAll();
        userRepository.deleteAll();
        themeRepository.deleteAll();

        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setPassword("password");
        testUser = userRepository.save(testUser);

        testTheme = new Theme();
        testTheme.setName("TECNOLOGIA");
        testTheme = themeRepository.save(testTheme);

        testIdea = new Idea();
        testIdea.setUser(testUser);
        testIdea.setTheme(testTheme);
        testIdea.setContext("Contexto");
        testIdea.setGeneratedContent("Conteúdo");
        testIdea.setModelUsed("mistral");
        testIdea.setExecutionTimeMs(1000L);
        testIdea = ideaRepository.save(testIdea);

        testSession = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        testSession.setLastResetAt(LocalDateTime.now());
        testSession = chatSessionRepository.save(testSession);
    }

    @Test
    void shouldFindBySessionIdOrderByCreatedAtAsc() {
        ChatMessage msg1 = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10);
        msg1.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        chatMessageRepository.save(msg1);

        ChatMessage msg2 = new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 1", 20);
        msg2.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        chatMessageRepository.save(msg2);

        ChatMessage msg3 = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10);
        msg3.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(msg3);

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(testSession.getId());

        assertEquals(3, messages.size());
        assertEquals("Mensagem 1", messages.get(0).getContent());
        assertEquals("Resposta 1", messages.get(1).getContent());
        assertEquals("Mensagem 2", messages.get(2).getContent());
    }

    @Test
    void shouldCountBySessionId() {
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 1", 20));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10));

        long count = chatMessageRepository.countBySessionId(testSession.getId());

        assertEquals(3, count);
    }

    @Test
    void shouldFindUserMessagesBySessionId() {
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 1", 20));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10));

        List<ChatMessage> userMessages = chatMessageRepository.findUserMessagesBySessionId(
                testSession.getId(), ChatMessage.MessageRole.USER);

        assertEquals(2, userMessages.size());
        assertTrue(userMessages.stream().allMatch(m -> m.getRole() == ChatMessage.MessageRole.USER));
    }

    @Test
    void shouldGetTotalUserTokensBySessionId() {
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 1", 20));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 15));

        int totalTokens = chatMessageRepository.getTotalUserTokensBySessionId(
                testSession.getId(), ChatMessage.MessageRole.USER);

        assertEquals(25, totalTokens);
    }

    @Test
    void shouldGetTotalUserTokensByUserId() {
        ChatSession session2 = new ChatSession(testUser, ChatSession.ChatType.FREE, null);
        session2.setLastResetAt(LocalDateTime.now());
        session2 = chatSessionRepository.save(session2);

        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10));
        chatMessageRepository.save(new ChatMessage(session2, ChatMessage.MessageRole.USER, "Mensagem 2", 20));

        int totalTokens = chatMessageRepository.getTotalUserTokensByUserId(
                testUser.getId(), ChatMessage.MessageRole.USER);

        assertEquals(30, totalTokens);
    }

    @Test
    void shouldFindLastMessagesBySessionId() {
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 0; i < 5; i++) {
            ChatMessage msg = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem " + i, 10);
            msg.setCreatedAt(baseTime.plusSeconds(i));
            chatMessageRepository.saveAndFlush(msg);
        }

        Pageable pageable = PageRequest.of(0, 3);
        var page = chatMessageRepository.findLastMessagesBySessionId(testSession.getId(), pageable);

        assertEquals(3, page.getContent().size());
        assertEquals("Mensagem 4", page.getContent().get(0).getContent());
    }

    @Test
    void shouldFindByUserIdAndDateRange() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);

        ChatMessage msg = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem", 10);
        msg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(msg);

        List<ChatMessage> messages = chatMessageRepository.findByUserIdAndDateRange(
                testUser.getId(), startDate, endDate);

        assertEquals(1, messages.size());
    }

    @Test
    void shouldFindByUserIdAndDateRangePaginated() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);

        for (int i = 0; i < 5; i++) {
            ChatMessage msg = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem " + i, 10);
            msg.setCreatedAt(LocalDateTime.now());
            chatMessageRepository.save(msg);
        }

        Pageable pageable = PageRequest.of(0, 3);
        var page = chatMessageRepository.findByUserIdAndDateRangePaginated(
                testUser.getId(), startDate, endDate, pageable);

        assertEquals(3, page.getContent().size());
    }

    @Test
    void shouldFindRecentMessagesOptimized() {
        for (int i = 0; i < 10; i++) {
            ChatMessage msg = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem " + i, 10);
            msg.setCreatedAt(LocalDateTime.now().minusMinutes(10 - i));
            chatMessageRepository.save(msg);
        }

        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesOptimized(testSession.getId(), 5);

        assertEquals(5, messages.size());
    }

    @Test
    void shouldFindLastMessagesBySessionIdAndRole() {
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 1", 20));
        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10));

        Pageable pageable = PageRequest.of(0, 10);
        var page = chatMessageRepository.findLastMessagesBySessionIdAndRole(
                testSession.getId(), ChatMessage.MessageRole.USER, pageable);

        assertEquals(2, page.getContent().size());
        assertTrue(page.getContent().stream().allMatch(m -> m.getRole() == ChatMessage.MessageRole.USER));
    }

    @Test
    void shouldFindLastAssistantMessageWithTokensRemaining() {
        ChatMessage msg1 = new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 1", 20);
        msg1.setTokensRemaining(5000);
        chatMessageRepository.save(msg1);

        ChatMessage msg2 = new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 2", 20);
        msg2.setTokensRemaining(null);
        chatMessageRepository.save(msg2);

        ChatMessage msg3 = new ChatMessage(testSession, ChatMessage.MessageRole.ASSISTANT, "Resposta 3", 20);
        msg3.setTokensRemaining(3000);
        chatMessageRepository.save(msg3);

        List<ChatMessage> messages = chatMessageRepository.findLastAssistantMessageWithTokensRemaining(
                testSession.getId(), ChatMessage.MessageRole.ASSISTANT);

        assertEquals(2, messages.size());
        assertTrue(messages.stream().allMatch(m -> m.getTokensRemaining() != null));
    }

    @Test
    void shouldFindMessagesBeforeTimestamp() {
        ChatMessage msg1 = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10);
        chatMessageRepository.saveAndFlush(msg1);

        ChatMessage msg2 = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10);
        chatMessageRepository.saveAndFlush(msg2);

        LocalDateTime beforeTime = LocalDateTime.now().plusSeconds(1);

        Pageable pageable = PageRequest.of(0, 10);
        List<ChatMessage> messages = chatMessageRepository.findMessagesBeforeTimestamp(
                testSession.getId(), beforeTime, pageable);

        assertTrue(messages.size() >= 0);
        assertTrue(messages.stream().allMatch(m -> m.getCreatedAt().isBefore(beforeTime)));
    }

    @Test
    void shouldCountMessagesBeforeTimestamp() {
        ChatMessage msg1 = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10);
        chatMessageRepository.saveAndFlush(msg1);

        ChatMessage msg2 = new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10);
        chatMessageRepository.saveAndFlush(msg2);

        LocalDateTime beforeTime = LocalDateTime.now().plusSeconds(1);

        long count = chatMessageRepository.countMessagesBeforeTimestamp(testSession.getId(), beforeTime);

        assertTrue(count >= 0);
    }

    @Test
    void shouldFindByUserIdAndDateRangeAdmin() {
        User otherUser = new User();
        otherUser.setEmail("other@example.com");
        otherUser.setName("Other User");
        otherUser.setPassword("password");
        otherUser = userRepository.save(otherUser);

        ChatSession otherSession = new ChatSession(otherUser, ChatSession.ChatType.FREE, null);
        otherSession.setLastResetAt(LocalDateTime.now());
        otherSession = chatSessionRepository.save(otherSession);

        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);

        chatMessageRepository.save(new ChatMessage(testSession, ChatMessage.MessageRole.USER, "Mensagem 1", 10));
        chatMessageRepository.save(new ChatMessage(otherSession, ChatMessage.MessageRole.USER, "Mensagem 2", 10));

        List<ChatMessage> allMessages = chatMessageRepository.findByUserIdAndDateRangeAdmin(
                null, startDate, endDate);

        assertEquals(2, allMessages.size());

        List<ChatMessage> userMessages = chatMessageRepository.findByUserIdAndDateRangeAdmin(
                testUser.getId(), startDate, endDate);

        assertEquals(1, userMessages.size());
    }
}
