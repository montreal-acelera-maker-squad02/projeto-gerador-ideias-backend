package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import projeto_gerador_ideias_backend.model.ChatMessage;
import projeto_gerador_ideias_backend.model.ChatSession;
import projeto_gerador_ideias_backend.model.Idea;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PromptBuilderServiceTest {

    @Mock
    private PromptSanitizer promptSanitizer;

    private PromptBuilderService promptBuilderService;

    @BeforeEach
    void setUp() {
        promptBuilderService = new PromptBuilderService(promptSanitizer);
    }

    @Test
    void shouldBuildSystemPromptForFreeChat() {
        String prompt = promptBuilderService.buildSystemPromptForFreeChat();

        assertNotNull(prompt);
        assertTrue(prompt.contains("assistente útil"));
        assertTrue(prompt.contains("MODERACAO: PERIGOSO"));
    }

    @Test
    void shouldBuildSystemPromptForIdeaChatWithCachedContent() {
        ChatSession session = mock(ChatSession.class);
        when(session.getType()).thenReturn(ChatSession.ChatType.IDEA_BASED);
        when(session.getCachedIdeaContent()).thenReturn("Test content");
        when(session.getCachedIdeaContext()).thenReturn("Test context");
        when(promptSanitizer.escapeForFormat("Test content")).thenReturn("Sanitized content");
        when(promptSanitizer.escapeForFormat("Test context")).thenReturn("Sanitized context");

        String prompt = promptBuilderService.buildSystemPromptForIdeaChat(session);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Sanitized content"));
        assertTrue(prompt.contains("Sanitized context"));
        assertTrue(prompt.contains("MODERACAO: PERIGOSO"));
        verify(session, never()).getIdea();
    }

    @Test
    void shouldBuildSystemPromptForIdeaChatWithIdeaWhenCacheIsNull() {
        ChatSession session = mock(ChatSession.class);
        Idea idea = mock(Idea.class);
        when(session.getType()).thenReturn(ChatSession.ChatType.IDEA_BASED);
        when(session.getCachedIdeaContent()).thenReturn(null);
        when(session.getCachedIdeaContext()).thenReturn(null);
        when(session.getIdea()).thenReturn(idea);
        when(idea.getGeneratedContent()).thenReturn("Idea content");
        when(idea.getContext()).thenReturn("Idea context");
        when(promptSanitizer.escapeForFormat("Idea content")).thenReturn("Sanitized idea content");
        when(promptSanitizer.escapeForFormat("Idea context")).thenReturn("Sanitized idea context");

        String prompt = promptBuilderService.buildSystemPromptForIdeaChat(session);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Sanitized idea content"));
        assertTrue(prompt.contains("Sanitized idea context"));
    }

    @Test
    void shouldBuildSystemPromptForIdeaChatWithEmptyCache() {
        ChatSession session = mock(ChatSession.class);
        when(session.getType()).thenReturn(ChatSession.ChatType.IDEA_BASED);
        when(session.getCachedIdeaContent()).thenReturn("");
        when(session.getCachedIdeaContext()).thenReturn("");
        when(promptSanitizer.escapeForFormat("")).thenReturn("");

        String prompt = promptBuilderService.buildSystemPromptForIdeaChat(session);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Ideia: \"\""));
        assertTrue(prompt.contains("Contexto: \"\""));
    }

    @Test
    void shouldBuildSystemPromptForIdeaChatWithoutIdea() {
        ChatSession session = mock(ChatSession.class);
        when(session.getType()).thenReturn(ChatSession.ChatType.IDEA_BASED);
        when(session.getCachedIdeaContent()).thenReturn(null);
        when(session.getCachedIdeaContext()).thenReturn(null);
        when(session.getIdea()).thenReturn(null);

        String prompt = promptBuilderService.buildSystemPromptForIdeaChat(session);

        assertNotNull(prompt);
        assertEquals("Você é um assistente útil e criativo. Responda de forma concisa e em português do Brasil.", prompt);
        verify(promptSanitizer, never()).escapeForFormat(anyString());
    }

    @Test
    void shouldBuildSystemPromptForFreeChatType() {
        ChatSession session = mock(ChatSession.class);
        when(session.getType()).thenReturn(ChatSession.ChatType.FREE);

        String prompt = promptBuilderService.buildSystemPromptForIdeaChat(session);

        assertNotNull(prompt);
        assertEquals("Você é um assistente útil e criativo. Responda de forma concisa e em português do Brasil.", prompt);
        verify(session, never()).getCachedIdeaContent();
        verify(session, never()).getCachedIdeaContext();
        verify(session, never()).getIdea();
    }

    @Test
    void shouldBuildMessageHistoryWithNullList() {
        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(null);

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void shouldBuildMessageHistoryWithEmptyList() {
        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of());

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void shouldBuildMessageHistoryWithValidMessages() {
        ChatMessage msg1 = createChatMessage("Hello", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage msg2 = createChatMessage("Hi there", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(1));

        when(promptSanitizer.sanitizeForPrompt("Hello")).thenReturn("Hello");
        when(promptSanitizer.sanitizeForPrompt("Hi there")).thenReturn("Hi there");

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2));

        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("Hello", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("Hi there", messages.get(1).getContent());
    }

    @Test
    void shouldBuildMessageHistoryFilteringNullMessages() {
        ChatMessage msg1 = createChatMessage("Hello", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage nullMsg = null;
        ChatMessage msg2 = createChatMessage("Hi", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(1));

        when(promptSanitizer.sanitizeForPrompt("Hello")).thenReturn("Hello");
        when(promptSanitizer.sanitizeForPrompt("Hi")).thenReturn("Hi");

        List<ChatMessage> messageList = new ArrayList<>();
        messageList.add(msg1);
        messageList.add(nullMsg);
        messageList.add(msg2);

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(messageList);

        assertNotNull(messages);
        assertEquals(2, messages.size());
    }

    @Test
    void shouldBuildMessageHistoryFilteringMessagesWithNullContent() {
        ChatMessage msg1 = createChatMessage("Hello", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage msg2 = createChatMessage(null, ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(1));
        ChatMessage msg3 = createChatMessage("Hi", ChatMessage.MessageRole.USER, LocalDateTime.now());

        when(promptSanitizer.sanitizeForPrompt("Hello")).thenReturn("Hello");
        when(promptSanitizer.sanitizeForPrompt("Hi")).thenReturn("Hi");

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2, msg3));

        assertNotNull(messages);
        assertEquals(2, messages.size());
    }

    @Test
    void shouldBuildMessageHistoryFilteringMessagesWithEmptyContent() {
        ChatMessage msg1 = createChatMessage("Hello", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage msg2 = createChatMessage("   ", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(1));
        ChatMessage msg3 = createChatMessage("Hi", ChatMessage.MessageRole.USER, LocalDateTime.now());

        when(promptSanitizer.sanitizeForPrompt("Hello")).thenReturn("Hello");
        when(promptSanitizer.sanitizeForPrompt("Hi")).thenReturn("Hi");

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2, msg3));

        assertNotNull(messages);
        assertEquals(2, messages.size());
    }

    @Test
    void shouldBuildMessageHistoryWithMoreThanThreeMessages() {
        ChatMessage msg1 = createChatMessage("Message 1", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(5));
        ChatMessage msg2 = createChatMessage("Message 2", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(4));
        ChatMessage msg3 = createChatMessage("Message 3", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(3));
        ChatMessage msg4 = createChatMessage("Message 4", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(2));
        ChatMessage msg5 = createChatMessage("Message 5", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(1));

        when(promptSanitizer.sanitizeForPrompt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2, msg3, msg4, msg5));

        assertNotNull(messages);
        assertEquals(3, messages.size());
        assertEquals("Message 3", messages.get(0).getContent());
        assertEquals("Message 4", messages.get(1).getContent());
        assertEquals("Message 5", messages.get(2).getContent());
    }

    @Test
    void shouldBuildMessageHistoryWithExactlyThreeMessages() {
        ChatMessage msg1 = createChatMessage("Message 1", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage msg2 = createChatMessage("Message 2", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(1));
        ChatMessage msg3 = createChatMessage("Message 3", ChatMessage.MessageRole.USER, LocalDateTime.now());

        when(promptSanitizer.sanitizeForPrompt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2, msg3));

        assertNotNull(messages);
        assertEquals(3, messages.size());
    }

    @Test
    void shouldBuildMessageHistoryWithLessThanThreeMessages() {
        ChatMessage msg1 = createChatMessage("Message 1", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(1));
        ChatMessage msg2 = createChatMessage("Message 2", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now());

        when(promptSanitizer.sanitizeForPrompt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2));

        assertNotNull(messages);
        assertEquals(2, messages.size());
    }

    @Test
    void shouldBuildMessageHistorySortingByCreatedAt() {
        LocalDateTime now = LocalDateTime.now();
        ChatMessage msg1 = createChatMessage("First", ChatMessage.MessageRole.USER, now.minusMinutes(3));
        ChatMessage msg2 = createChatMessage("Second", ChatMessage.MessageRole.ASSISTANT, now.minusMinutes(1));
        ChatMessage msg3 = createChatMessage("Third", ChatMessage.MessageRole.USER, now.minusMinutes(2));

        when(promptSanitizer.sanitizeForPrompt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2, msg3));

        assertNotNull(messages);
        assertEquals(3, messages.size());
        assertEquals("First", messages.get(0).getContent());
        assertEquals("Third", messages.get(1).getContent());
        assertEquals("Second", messages.get(2).getContent());
    }

    @Test
    void shouldBuildMessageHistoryMappingRolesCorrectly() {
        ChatMessage userMsg = createChatMessage("User message", ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(1));
        ChatMessage assistantMsg = createChatMessage("Assistant message", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now());

        when(promptSanitizer.sanitizeForPrompt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(userMsg, assistantMsg));

        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("assistant", messages.get(1).getRole());
    }

    @Test
    void shouldBuildMessageHistorySanitizingContent() {
        ChatMessage msg = createChatMessage("Original content", ChatMessage.MessageRole.USER, LocalDateTime.now());
        when(promptSanitizer.sanitizeForPrompt("Original content")).thenReturn("Sanitized content");

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg));

        assertNotNull(messages);
        assertEquals(1, messages.size());
        assertEquals("Sanitized content", messages.get(0).getContent());
        verify(promptSanitizer, times(1)).sanitizeForPrompt("Original content");
    }

    @Test
    void shouldBuildMessageHistoryWithAllInvalidMessages() {
        ChatMessage msg1 = createChatMessage(null, ChatMessage.MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage msg2 = createChatMessage("   ", ChatMessage.MessageRole.ASSISTANT, LocalDateTime.now().minusMinutes(1));

        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> messages = 
                promptBuilderService.buildMessageHistory(List.of(msg1, msg2));

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void shouldBuildSystemPromptForIdeaChatWithPartialCache() {
        ChatSession session = mock(ChatSession.class);
        Idea idea = mock(Idea.class);
        when(session.getType()).thenReturn(ChatSession.ChatType.IDEA_BASED);
        when(session.getCachedIdeaContent()).thenReturn("Cached content");
        when(session.getCachedIdeaContext()).thenReturn(null);
        when(session.getIdea()).thenReturn(idea);
        when(idea.getGeneratedContent()).thenReturn("Idea content");
        when(idea.getContext()).thenReturn("Idea context");
        when(promptSanitizer.escapeForFormat("Idea content")).thenReturn("Sanitized idea content");
        when(promptSanitizer.escapeForFormat("Idea context")).thenReturn("Sanitized idea context");

        String prompt = promptBuilderService.buildSystemPromptForIdeaChat(session);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Sanitized idea content"));
        assertTrue(prompt.contains("Sanitized idea context"));
    }

    private ChatMessage createChatMessage(String content, ChatMessage.MessageRole role, LocalDateTime createdAt) {
        ChatMessage msg = mock(ChatMessage.class);
        when(msg.getContent()).thenReturn(content);
        when(msg.getRole()).thenReturn(role);
        when(msg.getCreatedAt()).thenReturn(createdAt);
        return msg;
    }
}


