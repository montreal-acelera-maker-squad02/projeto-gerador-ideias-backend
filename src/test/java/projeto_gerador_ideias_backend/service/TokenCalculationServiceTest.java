package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldReturnZeroWhenEstimateTokensWithInvalidInput(String text) {
        int tokens = tokenCalculationService.estimateTokens(text);

        assertEquals(0, tokens);
    }

    @Test
    void shouldReturnAtLeastOneTokenForNonEmptyText() {
        int tokens = tokenCalculationService.estimateTokens("a");

        assertTrue(tokens >= 1);
    }

    @ParameterizedTest
    @MethodSource("provideEstimateTokensCases")
    void shouldEstimateTokensForVariousTexts(String text) {
        int tokens = tokenCalculationService.estimateTokens(text);

        assertTrue(tokens >= 1);
    }

    private static java.util.stream.Stream<Arguments> provideEstimateTokensCases() {
        return java.util.stream.Stream.of(
                Arguments.of("Hello world"),
                Arguments.of("This is a very long text with many words that should trigger the word count based estimation algorithm because it has more than ten words in total"),
                Arguments.of("!!!@@@###$$$%%%^^^&&&***((()))"),
                Arguments.of("!!!!!aaaaa"),
                Arguments.of("This is a normal text with some words"),
                Arguments.of("Hello 世界 🌍"),
                Arguments.of("Hello! This is a test with 123 numbers and @#$ special characters."),
                Arguments.of("Hello"),
                Arguments.of("Line 1\nLine 2\nLine 3"),
                Arguments.of("Word1\tWord2\tWord3"),
                Arguments.of("Word1    Word2    Word3"),
                Arguments.of("Hello, world! How are you? I'm fine."),
                Arguments.of("The number is 12345 and the price is $99.99"),
                Arguments.of("one two three four five six seven eight nine ten"),
                Arguments.of("one two three four five six seven eight nine ten eleven twelve"),
                Arguments.of("one two three four five"),
                Arguments.of("   Hello world   "),
                Arguments.of("a".repeat(1000)),
                Arguments.of("!@#$%^&*()"),
                Arguments.of("abcdefghijklmnopqrstuvwxyz"),
                Arguments.of("1234567890")
        );
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


