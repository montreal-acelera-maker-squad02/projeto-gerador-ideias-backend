package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ContentModerationServiceTest {

    private ContentModerationService contentModerationService;

    @BeforeEach
    void setUp() {
        contentModerationService = new ContentModerationService();
    }

    @Test
    void shouldNormalizeDangerousContentForFreeChat() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        String result = contentModerationService.validateAndNormalizeResponse(dangerousContent, true);

        assertEquals("Desculpe, não posso processar essa mensagem devido ao conteúdo. Posso ajudá-lo com outras questões?", result);
    }

    @Test
    void shouldNormalizeDangerousContentForIdeaBasedChat() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        String result = contentModerationService.validateAndNormalizeResponse(dangerousContent, false);

        assertEquals("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia. Como posso ajudá-lo a desenvolver ou melhorar esta ideia?", result);
    }

    @Test
    void shouldReturnSafeContentAsIs() {
        String safeContent = "Esta é uma resposta segura e útil.";

        String result1 = contentModerationService.validateAndNormalizeResponse(safeContent, true);
        String result2 = contentModerationService.validateAndNormalizeResponse(safeContent, false);

        assertEquals(safeContent, result1);
        assertEquals(safeContent, result2);
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
        String result = contentModerationService.validateAndNormalizeResponse(dangerousContent, true);

        assertTrue(result.contains("não posso processar"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Esta é uma resposta [MODERACAO: PERIGOSO] no meio do texto",
        "Esta é uma resposta segura [MODERACAO: PERIGOSO]",
        "Texto antes [MODERACAO: PERIGOSO] texto depois"
    })
    void shouldNotDetectDangerousContentWhenTagIsNotAtStart(String safeContent) {
        String result = contentModerationService.validateAndNormalizeResponse(safeContent, true);

        assertEquals(safeContent, result);
    }

    @Test
    void shouldHandleDifferentFreeChatScenarios() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        String result1 = contentModerationService.validateAndNormalizeResponse(dangerousContent, true);
        String result2 = contentModerationService.validateAndNormalizeResponse(dangerousContent, true);

        assertEquals(result1, result2);
        assertTrue(result1.contains("não posso processar"));
    }

    @Test
    void shouldHandleDifferentIdeaBasedChatScenarios() {
        String dangerousContent = "[MODERACAO: PERIGOSO]";

        String result1 = contentModerationService.validateAndNormalizeResponse(dangerousContent, false);
        String result2 = contentModerationService.validateAndNormalizeResponse(dangerousContent, false);

        assertEquals(result1, result2);
        assertTrue(result1.contains("não está relacionada à ideia"));
    }

    @Test
    void shouldNotDetectSafeModerationTag() {
        String safeContent = "[MODERACAO: SEGURA] Esta é uma resposta segura";

        String result = contentModerationService.validateAndNormalizeResponse(safeContent, true);

        assertEquals(safeContent, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "   [MODERACAO: PERIGOSO]",
        "[MODERACAO: PERIGOSO]   ",
        "  [MODERACAO: PERIGOSO]  ",
        "\t[MODERACAO: PERIGOSO]\t",
        "\n[MODERACAO: PERIGOSO]\n"
    })
    void shouldHandleContentWithModerationTagAndWhitespace(String dangerousContent) {
        String resultFreeChat = contentModerationService.validateAndNormalizeResponse(dangerousContent, true);
        String resultIdeaChat = contentModerationService.validateAndNormalizeResponse(dangerousContent, false);

        assertTrue(resultFreeChat.contains("não posso processar"));
        assertTrue(resultIdeaChat.contains("não está relacionada à ideia"));
    }
}

