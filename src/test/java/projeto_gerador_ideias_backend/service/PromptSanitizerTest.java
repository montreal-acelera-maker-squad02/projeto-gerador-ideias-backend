package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptSanitizerTest {

    private PromptSanitizer promptSanitizer;

    @BeforeEach
    void setUp() {
        promptSanitizer = new PromptSanitizer();
    }

    @Test
    void shouldReturnEmptyStringWhenContentIsNull() {
        String result = promptSanitizer.sanitizeForPrompt(null);
        assertEquals("", result);
    }

    @Test
    void shouldReplaceCarriageReturnLineFeedWithLineFeed() {
        String content = "Line1\r\nLine2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Line1\nLine2", result);
    }

    @Test
    void shouldReplaceCarriageReturnWithLineFeed() {
        String content = "Line1\rLine2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Line1\nLine2", result);
    }

    @Test
    void shouldReplaceTabWithSpace() {
        String content = "Word1\tWord2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Word1 Word2", result);
    }

    @Test
    void shouldReplaceMultipleSpacesWithSingleSpace() {
        String content = "Word1    Word2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Word1 Word2", result);
    }

    @Test
    void shouldReplaceMultipleTabsWithSingleSpace() {
        String content = "Word1\t\t\tWord2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Word1 Word2", result);
    }

    @Test
    void shouldReplaceMixedSpacesAndTabsWithSingleSpace() {
        String content = "Word1 \t \t Word2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Word1 Word2", result);
    }

    @Test
    void shouldReplaceThreeOrMoreNewlinesWithTwoNewlines() {
        String content = "Line1\n\n\nLine2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Line1\n\nLine2", result);
    }

    @Test
    void shouldReplaceFourNewlinesWithTwoNewlines() {
        String content = "Line1\n\n\n\nLine2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Line1\n\nLine2", result);
    }

    @Test
    void shouldNotReplaceTwoNewlines() {
        String content = "Line1\n\nLine2";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Line1\n\nLine2", result);
    }

    @Test
    void shouldTrimLeadingAndTrailingWhitespace() {
        String content = "  Content  ";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Content", result);
    }

    @Test
    void shouldHandleAllTransformationsTogether() {
        String content = "  Line1\r\n\t  Line2\r\t\tLine3\n\n\nLine4  ";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("Line1\n Line2\n Line3\n\nLine4", result);
    }

    @Test
    void shouldReturnEmptyStringWhenContentIsOnlyWhitespace() {
        String content = "   \t\t  \n\n  ";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("", result);
    }

    @Test
    void shouldEscapeForFormatReturnEmptyStringWhenContentIsNull() {
        String result = promptSanitizer.escapeForFormat(null);
        assertEquals("", result);
    }

    @Test
    void shouldEscapePercentSign() {
        String content = "50% discount";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("50%% discount", result);
    }

    @Test
    void shouldEscapeMultiplePercentSigns() {
        String content = "50% off and 25% more";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("50%% off and 25%% more", result);
    }

    @Test
    void shouldEscapeOpeningBrace() {
        String content = "Value {placeholder}";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("Value {{placeholder}}", result);
    }

    @Test
    void shouldEscapeClosingBrace() {
        String content = "Value {placeholder}";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("Value {{placeholder}}", result);
    }

    @Test
    void shouldEscapeAllFormatCharacters() {
        String content = "Price: {value} with 50% discount";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("Price: {{value}} with 50%% discount", result);
    }

    @Test
    void shouldEscapeForFormatAlsoSanitizeContent() {
        String content = "  Line1\r\n\t  Line2  ";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("Line1\n Line2", result);
    }

    @Test
    void shouldEscapeForFormatWithAllTransformations() {
        String content = "  Price: {value} with 50% discount\r\n\t  ";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("Price: {{value}} with 50%% discount", result);
    }

    @Test
    void shouldHandleEmptyString() {
        String result = promptSanitizer.sanitizeForPrompt("");
        assertEquals("", result);
    }

    @Test
    void shouldEscapeForFormatHandleEmptyString() {
        String result = promptSanitizer.escapeForFormat("");
        assertEquals("", result);
    }

    @Test
    void shouldHandleContentWithOnlyNewlines() {
        String content = "\n\n\n";
        String result = promptSanitizer.sanitizeForPrompt(content);
        assertEquals("", result);
    }

    @Test
    void shouldEscapeForFormatHandleContentWithOnlyNewlines() {
        String content = "\n\n\n";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("", result);
    }

    @Test
    void shouldNotEscapeAlreadyEscapedPercent() {
        String content = "50%% discount";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("50%%%% discount", result);
    }

    @Test
    void shouldNotEscapeAlreadyEscapedBraces() {
        String content = "{{value}}";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("{{{{value}}}}", result);
    }
}

