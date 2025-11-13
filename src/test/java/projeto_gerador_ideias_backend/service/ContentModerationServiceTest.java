package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import projeto_gerador_ideias_backend.exceptions.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

class ContentModerationServiceTest {

    private ContentModerationService contentModerationService;

    @BeforeEach
    void setUp() {
        contentModerationService = new ContentModerationService();
    }

    @Test
    void shouldThrowExceptionForFreeChatWhenContentIsDangerous() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        assertEquals("Desculpe, não posso processar essa mensagem devido ao conteúdo.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionForIdeaBasedChatWhenContentIsDangerous() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, false);
        });

        assertEquals("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia.", exception.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenContentIsSafe() {
        String safeContent = "Esta é uma resposta segura e útil.";

        assertDoesNotThrow(() -> {
            contentModerationService.validateModerationResponse(safeContent, true);
        });

        assertDoesNotThrow(() -> {
            contentModerationService.validateModerationResponse(safeContent, false);
        });
    }

    @Test
    void shouldNotThrowExceptionWhenContentIsNull() {
        // Para free chat, null é retornado como está (não é normalizado)
        String result1 = contentModerationService.validateAndNormalizeResponse(null, true);
        assertNull(result1);
        
        // Para chat baseado em ideia, resposta null é normalizada
        String result2 = contentModerationService.validateAndNormalizeResponse(null, false);
        assertNotNull(result2);
        assertEquals("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia. Como posso ajudá-lo a desenvolver ou melhorar esta ideia?", result2);
    }

    @Test
    void shouldNotThrowExceptionWhenContentIsBlank() {
        String result1 = contentModerationService.validateAndNormalizeResponse("   ", true);
        assertNotNull(result1);
        assertEquals("   ", result1); // Para free chat, conteúdo em branco é retornado como está
        
        String result2 = contentModerationService.validateAndNormalizeResponse("", false);
        assertNotNull(result2);
        // Para chat baseado em ideia, resposta vazia é normalizada
        assertEquals("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia. Como posso ajudá-lo a desenvolver ou melhorar esta ideia?", result2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[MODERACAO: PERIGOSO]",
        "[moderacao: perigoso]",
        "[Moderacao: Perigoso]",
        "[MODERACAO:perigoso]",
        "[moderacao:PERIGOSO]",
        "  [MODERACAO: PERIGOSO]",
        "[MODERACAO : PERIGOSO]",
        "[MODERACAO:  PERIGOSO]",
        "[MODERAÇÃO: PERIGOSO]",
        "[MODERACAO: PERIGOSO] Algum conteúdo adicional"
    })
    void shouldDetectDangerousContentVariations(String dangerousContent) {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        assertTrue(exception.getMessage().contains("não posso processar"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Esta é uma resposta [MODERACAO: PERIGOSO] no meio do texto",
        "Esta é uma resposta segura [MODERACAO: PERIGOSO]",
        "Texto antes [MODERACAO: PERIGOSO] texto depois"
    })
    void shouldNotDetectDangerousContentWhenTagIsNotAtStart(String safeContent) {
        assertDoesNotThrow(() -> {
            contentModerationService.validateModerationResponse(safeContent, true);
        });
    }

    @Test
    void shouldHandleDifferentFreeChatScenarios() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        ValidationException exception1 = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        ValidationException exception2 = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        assertEquals(exception1.getMessage(), exception2.getMessage());
        assertTrue(exception1.getMessage().contains("não posso processar"));
    }

    @Test
    void shouldHandleDifferentIdeaBasedChatScenarios() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        ValidationException exception1 = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, false);
        });

        ValidationException exception2 = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, false);
        });

        assertEquals(exception1.getMessage(), exception2.getMessage());
        assertTrue(exception1.getMessage().contains("não está relacionada à ideia"));
    }

    @Test
    void shouldNotDetectSafeModerationTag() {
        String safeContent = "[MODERACAO: SEGURA] Esta é uma resposta segura";

        assertDoesNotThrow(() -> {
            contentModerationService.validateModerationResponse(safeContent, true);
        });
    }

    @Test
    void shouldHandleContentWithOnlyModerationTag() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        assertTrue(exception.getMessage().contains("não posso processar"));
    }

    @Test
    void shouldHandleContentWithLeadingWhitespace() {
        String dangerousContent = "   [MODERACAO: PERIGOSO]";

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        assertTrue(exception.getMessage().contains("não posso processar"));
    }

    @Test
    void shouldHandleContentWithTrailingWhitespace() {
        String dangerousContent = "[MODERACAO: PERIGOSO]   ";

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            contentModerationService.validateModerationResponse(dangerousContent, true);
        });

        assertTrue(exception.getMessage().contains("não posso processar"));
    }
}

