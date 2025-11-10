package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.exceptions.TokenLimitExceededException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.model.ChatSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ChatLimitValidatorTest {

    @Mock
    private ChatProperties chatProperties;

    @Mock
    private TokenCalculationService tokenCalculationService;

    @Mock
    private ChatSession chatSession;

    private ChatLimitValidator chatLimitValidator;

    @BeforeEach
    void setUp() {
        chatLimitValidator = new ChatLimitValidator(chatProperties, tokenCalculationService);
        when(chatSession.getId()).thenReturn(1L);
    }

    @Test
    void shouldThrowExceptionWhenMessageIsNull() {
        assertThrows(ValidationException.class, () -> {
            chatLimitValidator.validateMessageLimitsAndGetTokens(null);
        });
    }

    @Test
    void shouldThrowExceptionWhenMessageIsBlank() {
        assertThrows(ValidationException.class, () -> {
            chatLimitValidator.validateMessageLimitsAndGetTokens("   ");
        });
    }

    @Test
    void shouldThrowExceptionWhenMessageIsEmpty() {
        assertThrows(ValidationException.class, () -> {
            chatLimitValidator.validateMessageLimitsAndGetTokens("");
        });
    }

    @Test
    void shouldThrowExceptionWhenMessageLengthExceedsMaxChars() {
        String longMessage = "a".repeat(1001);
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            chatLimitValidator.validateMessageLimitsAndGetTokens(longMessage);
        });

        assertTrue(exception.getMessage().contains("excede o limite de 1000 caracteres"));
        assertTrue(exception.getMessage().contains("1001"));
    }

    @Test
    void shouldThrowExceptionWhenMessageBytesExceedsMaxBytes() {
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(500);
        
        byte[] bytes = new byte[1001];
        java.util.Arrays.fill(bytes, (byte) 'a');
        String messageWithManyBytes = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        when(tokenCalculationService.estimateTokens(messageWithManyBytes)).thenReturn(1);

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            chatLimitValidator.validateMessageLimitsAndGetTokens(messageWithManyBytes);
        });

        assertTrue(exception.getMessage().contains("excede o limite de tamanho") || 
                   exception.getMessage().contains("excede o limite de"));
    }

    @Test
    void shouldThrowExceptionWhenMessageTokensExceedsMaxTokensPerMessage() {
        String message = "test message";
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        when(chatProperties.getMaxTokensPerMessage()).thenReturn(100);
        when(tokenCalculationService.estimateTokens(message)).thenReturn(101);

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            chatLimitValidator.validateMessageLimitsAndGetTokens(message);
        });

        assertTrue(exception.getMessage().contains("excede o limite de 100 tokens"));
    }

    @Test
    void shouldReturnTokensWhenMessageIsValid() {
        String message = "valid message";
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        when(chatProperties.getMaxTokensPerMessage()).thenReturn(100);
        when(tokenCalculationService.estimateTokens(message)).thenReturn(10);

        int tokens = chatLimitValidator.validateMessageLimitsAndGetTokens(message);

        assertEquals(10, tokens);
        verify(tokenCalculationService, times(1)).estimateTokens(message);
    }

    @Test
    void shouldValidateMessageWithExactMaxChars() {
        String message = "a".repeat(1000);
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        when(chatProperties.getMaxTokensPerMessage()).thenReturn(100);
        when(tokenCalculationService.estimateTokens(message)).thenReturn(10);

        int tokens = chatLimitValidator.validateMessageLimitsAndGetTokens(message);

        assertEquals(10, tokens);
    }

    @Test
    void shouldValidateMessageWithExactMaxTokens() {
        String message = "test";
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        when(chatProperties.getMaxTokensPerMessage()).thenReturn(100);
        when(tokenCalculationService.estimateTokens(message)).thenReturn(100);

        int tokens = chatLimitValidator.validateMessageLimitsAndGetTokens(message);

        assertEquals(100, tokens);
    }

    @Test
    void shouldThrowExceptionWhenChatNotBlockedWithAdditionalTokensExceedsLimit() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(9999);

        assertThrows(TokenLimitExceededException.class, () -> {
            chatLimitValidator.validateChatNotBlocked(chatSession, 1);
        });
    }

    @Test
    void shouldThrowExceptionWhenChatNotBlockedWithAdditionalTokensEqualsLimit() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(5000);

        assertThrows(TokenLimitExceededException.class, () -> {
            chatLimitValidator.validateChatNotBlocked(chatSession, 5000);
        });
    }

    @Test
    void shouldNotThrowExceptionWhenChatNotBlockedWithValidTokens() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(5000);

        assertDoesNotThrow(() -> {
            chatLimitValidator.validateChatNotBlocked(chatSession, 4999);
        });
    }

    @Test
    void shouldThrowExceptionWhenValidateChatNotBlockedWithResponseExceedsLimit() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(5000);

        assertThrows(TokenLimitExceededException.class, () -> {
            chatLimitValidator.validateChatNotBlockedWithResponse(chatSession, 3000, 2001);
        });
    }

    @Test
    void shouldNotThrowExceptionWhenValidateChatNotBlockedWithResponseEqualsLimit() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(5000);

        assertDoesNotThrow(() -> {
            chatLimitValidator.validateChatNotBlockedWithResponse(chatSession, 3000, 2000);
        });
    }

    @Test
    void shouldNotThrowExceptionWhenValidateChatNotBlockedWithResponseValid() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(5000);

        assertDoesNotThrow(() -> {
            chatLimitValidator.validateChatNotBlockedWithResponse(chatSession, 3000, 1999);
        });
    }

    @Test
    void shouldThrowExceptionWhenValidateChatNotBlockedWithResponseExceedsLimitByOne() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(5000);

        assertThrows(TokenLimitExceededException.class, () -> {
            chatLimitValidator.validateChatNotBlockedWithResponse(chatSession, 3000, 2001);
        });
    }

    @Test
    void shouldReturnTrueWhenIsChatBlocked() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10000);

        boolean isBlocked = chatLimitValidator.isChatBlocked(chatSession);

        assertTrue(isBlocked);
    }

    @Test
    void shouldReturnTrueWhenIsChatBlockedExceedsLimit() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10001);

        boolean isBlocked = chatLimitValidator.isChatBlocked(chatSession);

        assertTrue(isBlocked);
    }

    @Test
    void shouldReturnFalseWhenIsChatBlockedBelowLimit() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(9999);

        boolean isBlocked = chatLimitValidator.isChatBlocked(chatSession);

        assertFalse(isBlocked);
    }

    @Test
    void shouldThrowExceptionWhenValidateSessionNotBlockedIsBlocked() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(10000);

        assertThrows(TokenLimitExceededException.class, () -> {
            chatLimitValidator.validateSessionNotBlocked(chatSession);
        });
    }

    @Test
    void shouldNotThrowExceptionWhenValidateSessionNotBlockedIsNotBlocked() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(9999);

        assertDoesNotThrow(() -> {
            chatLimitValidator.validateSessionNotBlocked(chatSession);
        });
    }

    @Test
    void shouldHandleZeroTokensInValidateChatNotBlocked() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);

        assertDoesNotThrow(() -> {
            chatLimitValidator.validateChatNotBlocked(chatSession, 0);
        });
    }

    @Test
    void shouldHandleZeroTokensInValidateChatNotBlockedWithResponse() {
        when(chatProperties.getMaxTokensPerChat()).thenReturn(10000);
        when(tokenCalculationService.getTotalUserTokensInChat(1L)).thenReturn(0);

        assertDoesNotThrow(() -> {
            chatLimitValidator.validateChatNotBlockedWithResponse(chatSession, 0, 0);
        });
    }

    @Test
    void shouldHandleUnicodeCharactersInMessage() {
        String unicodeMessage = "Hello 世界 🌍";
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        when(chatProperties.getMaxTokensPerMessage()).thenReturn(100);
        when(tokenCalculationService.estimateTokens(unicodeMessage)).thenReturn(5);

        int tokens = chatLimitValidator.validateMessageLimitsAndGetTokens(unicodeMessage);

        assertEquals(5, tokens);
    }

    @Test
    void shouldCalculateMaxBytesCorrectly() {
        String message = "a".repeat(500);
        when(chatProperties.getMaxCharsPerMessage()).thenReturn(1000);
        when(chatProperties.getMaxTokensPerMessage()).thenReturn(100);
        when(tokenCalculationService.estimateTokens(message)).thenReturn(10);

        int tokens = chatLimitValidator.validateMessageLimitsAndGetTokens(message);

        assertEquals(10, tokens);
    }
}


