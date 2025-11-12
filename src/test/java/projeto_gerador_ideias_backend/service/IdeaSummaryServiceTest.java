package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdeaSummaryServiceTest {

    private IdeaSummaryService ideaSummaryService;

    @BeforeEach
    void setUp() {
        ideaSummaryService = new IdeaSummaryService();
    }

    @Test
    void shouldReturnEmptyStringWhenContentIsNull() {
        String result = ideaSummaryService.summarizeIdeaSimple(null);
        assertEquals("", result);
    }

    @Test
    void shouldReturnEmptyStringWhenContentIsEmpty() {
        String result = ideaSummaryService.summarizeIdeaSimple("");
        assertEquals("", result);
    }

    @Test
    void shouldReturnEmptyStringWhenContentIsBlank() {
        String result = ideaSummaryService.summarizeIdeaSimple("   ");
        assertEquals("", result);
    }

    @Test
    void shouldReturnFullContentWhenHasTenWordsOrLess() {
        String content = "Esta é uma ideia simples com dez palavras exatas";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        assertEquals(content, result);
    }

    @Test
    void shouldReturnFullContentWhenHasLessThanTenWords() {
        String content = "Esta é uma ideia simples";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        assertEquals(content, result);
    }

    @Test
    void shouldSummarizeWhenContentHasMoreThanTenWords() {
        String content = "Esta é uma ideia muito longa que precisa ser resumida porque tem mais de dez palavras no total";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.length() < content.length());
        assertTrue(result.split("\\s+").length <= 5);
    }

    @Test
    void shouldSummarizeByCommaPunctuation() {
        String content = "Primeira parte, segunda parte, terceira parte, quarta parte, quinta parte, sexta parte, sétima parte, oitava parte, nona parte, décima parte, décima primeira parte";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.contains("Primeira parte"));
        assertTrue(result.split("\\s+").length <= 5);
    }

    @Test
    void shouldSummarizeBySemicolonPunctuation() {
        String content = "Primeira parte; segunda parte; terceira parte; quarta parte; quinta parte; sexta parte; sétima parte; oitava parte; nona parte; décima parte; décima primeira parte";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.contains("Primeira parte"));
        assertTrue(result.split("\\s+").length <= 5);
    }

    @Test
    void shouldSummarizeByPeriodPunctuation() {
        String content = "Primeira parte. Segunda parte. Terceira parte. Quarta parte. Quinta parte. Sexta parte. Sétima parte. Oitava parte. Nona parte. Décima parte. Décima primeira parte.";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.contains("Primeira parte"));
        assertTrue(result.split("\\s+").length <= 5);
    }

    @Test
    void shouldRemoveIncompleteWordsAtEnd() {
        String content = "Esta é uma ideia muito interessante com várias palavras importantes para demonstrar o funcionamento do sistema de resumo automático que remove palavras incompletas";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        String[] words = result.split("\\s+");
        assertTrue(words.length <= 5);
        
        String lastWord = words[words.length - 1].toLowerCase();
        assertFalse(lastWord.equals("com") || lastWord.equals("de") || lastWord.equals("para"));
    }

    @Test
    void shouldRemoveTrailingPunctuation() {
        String content = "Primeira parte, segunda parte, terceira parte, quarta parte, quinta parte, sexta parte, sétima parte, oitava parte, nona parte, décima parte, décima primeira parte";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);

        assertFalse(result.endsWith(","));
        assertFalse(result.endsWith(";"));
        assertFalse(result.endsWith(":"));
        assertFalse(result.endsWith("-"));
    }

    @Test
    void shouldHandleContentWithMultipleSpaces() {
        String content = "Esta    é    uma    ideia    com    muitos    espaços    entre    as    palavras    que    precisa    ser    processada";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);

        assertFalse(result.contains("  "));
    }

    @Test
    void shouldHandleVeryLongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("palavra ").append(i).append(" ");
        }
        
        String result = ideaSummaryService.summarizeIdeaSimple(longContent.toString());
        
        assertNotNull(result);
        assertTrue(result.length() < longContent.length());
        assertTrue(result.split("\\s+").length <= 5);
    }

    @Test
    void shouldHandleContentWithSpecialCharacters() {
        String content = "Ideia com caracteres especiais: ponto-e-vírgula; vírgula, ponto final. Exclamação! Interrogação? Dois pontos:";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.split("\\s+").length <= 5);
    }

    @Test
    void shouldReturnMinimumThreeWordsWhenNoPunctuationFound() {
        String content = "Palavra um palavra dois palavra três palavra quatro palavra cinco palavra seis palavra sete palavra oito palavra nove palavra dez palavra onze";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        String[] words = result.split("\\s+");
        assertTrue(words.length >= 3);
        assertTrue(words.length <= 5);
    }

    @Test
    void shouldHandleContentWithOnlyPunctuation() {
        String content = ", , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , .";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
    }

    @Test
    void shouldHandleContentWithExactlyElevenWords() {
        String content = "Um dois três quatro cinco seis sete oito nove dez onze";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.split("\\s+").length <= 5);
        assertTrue(result.length() < content.length());
    }

    @Test
    void shouldPreserveFirstWordsWhenSummarizing() {
        String content = "Primeira segunda terceira quarta quinta sexta sétima oitava nona décima décima primeira décima segunda";
        String result = ideaSummaryService.summarizeIdeaSimple(content);
        
        assertNotNull(result);
        assertTrue(result.startsWith("Primeira"));
    }
}

