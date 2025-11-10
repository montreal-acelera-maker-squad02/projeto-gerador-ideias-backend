package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import projeto_gerador_ideias_backend.model.ChatMessage;
import projeto_gerador_ideias_backend.repository.ChatMessageRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCalculationServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private TokenCalculationService tokenCalculationService;

    @BeforeEach
    void setUp() {
        tokenCalculationService = new TokenCalculationService(chatMessageRepository);
    }

    @Test
    void shouldGetTotalUserTokensInChat() {
        Long sessionId = 1L;
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId, ChatMessage.MessageRole.USER))
                .thenReturn(100);
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId, ChatMessage.MessageRole.ASSISTANT))
                .thenReturn(200);

        int totalTokens = tokenCalculationService.getTotalUserTokensInChat(sessionId);

        assertEquals(300, totalTokens);
        verify(chatMessageRepository, times(1))
                .getTotalUserTokensBySessionId(sessionId, ChatMessage.MessageRole.USER);
        verify(chatMessageRepository, times(1))
                .getTotalUserTokensBySessionId(sessionId, ChatMessage.MessageRole.ASSISTANT);
    }

    @Test
    void shouldGetTotalUserTokensInChatWithZeroTokens() {
        Long sessionId = 1L;
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId, ChatMessage.MessageRole.USER))
                .thenReturn(0);
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId, ChatMessage.MessageRole.ASSISTANT))
                .thenReturn(0);

        int totalTokens = tokenCalculationService.getTotalUserTokensInChat(sessionId);

        assertEquals(0, totalTokens);
    }

    @Test
    void shouldGetTotalTokensUsedByUser() {
        Long userId = 1L;
        when(chatMessageRepository.getTotalUserTokensByUserId(userId, ChatMessage.MessageRole.USER))
                .thenReturn(500);

        int totalTokens = tokenCalculationService.getTotalTokensUsedByUser(userId);

        assertEquals(500, totalTokens);
        verify(chatMessageRepository, times(1))
                .getTotalUserTokensByUserId(userId, ChatMessage.MessageRole.USER);
    }

    @Test
    void shouldGetTotalTokensUsedByUserWithZeroTokens() {
        Long userId = 1L;
        when(chatMessageRepository.getTotalUserTokensByUserId(userId, ChatMessage.MessageRole.USER))
                .thenReturn(0);

        int totalTokens = tokenCalculationService.getTotalTokensUsedByUser(userId);

        assertEquals(0, totalTokens);
    }

    @Test
    void shouldReturnZeroWhenEstimateTokensWithNull() {
        int tokens = tokenCalculationService.estimateTokens(null);

        assertEquals(0, tokens);
    }

    @Test
    void shouldReturnZeroWhenEstimateTokensWithEmptyString() {
        int tokens = tokenCalculationService.estimateTokens("");

        assertEquals(0, tokens);
    }

    @Test
    void shouldReturnZeroWhenEstimateTokensWithWhitespaceOnly() {
        int tokens = tokenCalculationService.estimateTokens("   ");

        assertEquals(0, tokens);
    }

    @Test
    void shouldReturnAtLeastOneTokenForNonEmptyText() {
        int tokens = tokenCalculationService.estimateTokens("a");

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForShortText() {
        String text = "Hello world";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForLongTextWithManyWords() {
        String text = "This is a very long text with many words that should trigger the word count based estimation algorithm because it has more than ten words in total";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithManySpecialChars() {
        String text = "!!!@@@###$$$%%%^^^&&&***((()))";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithSpecialCharsGreaterThan20Percent() {
        String text = "!!!!!aaaaa";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForNormalText() {
        String text = "This is a normal text with some words";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithUnicode() {
        String text = "Hello 世界 🌍";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithMixedContent() {
        String text = "Hello! This is a test with 123 numbers and @#$ special characters.";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForSingleWord() {
        String text = "Hello";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithNewlines() {
        String text = "Line 1\nLine 2\nLine 3";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithTabs() {
        String text = "Word1\tWord2\tWord3";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithMultipleSpaces() {
        String text = "Word1    Word2    Word3";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithPunctuation() {
        String text = "Hello, world! How are you? I'm fine.";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithNumbers() {
        String text = "The number is 12345 and the price is $99.99";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithExactlyTenWords() {
        String text = "one two three four five six seven eight nine ten";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithMoreThanTenWords() {
        String text = "one two three four five six seven eight nine ten eleven twelve";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithLessThanTenWords() {
        String text = "one two three four five";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithLeadingAndTrailingWhitespace() {
        String text = "   Hello world   ";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForVeryLongText() {
        String text = "a".repeat(1000);
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithOnlySpecialChars() {
        String text = "!@#$%^&*()";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithOnlyLetters() {
        String text = "abcdefghijklmnopqrstuvwxyz";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldEstimateTokensForTextWithOnlyNumbers() {
        String text = "1234567890";
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    @Test
    void shouldGetTotalUserTokensInChatWithDifferentSessionIds() {
        Long sessionId1 = 1L;
        Long sessionId2 = 2L;
        
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId1, ChatMessage.MessageRole.USER))
                .thenReturn(50);
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId1, ChatMessage.MessageRole.ASSISTANT))
                .thenReturn(100);
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId2, ChatMessage.MessageRole.USER))
                .thenReturn(200);
        when(chatMessageRepository.getTotalUserTokensBySessionId(sessionId2, ChatMessage.MessageRole.ASSISTANT))
                .thenReturn(300);

        int totalTokens1 = tokenCalculationService.getTotalUserTokensInChat(sessionId1);
        int totalTokens2 = tokenCalculationService.getTotalUserTokensInChat(sessionId2);

        assertEquals(150, totalTokens1);
        assertEquals(500, totalTokens2);
    }
}


