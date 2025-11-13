package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class PromptSanitizerTest {

    private PromptSanitizer promptSanitizer;

    @BeforeEach
    void setUp() {
        promptSanitizer = new PromptSanitizer();
    }

    @ParameterizedTest
    @MethodSource("provideSanitizeForPromptCases")
    void shouldSanitizeForPrompt(String input, String expected) {
        String result = promptSanitizer.sanitizeForPrompt(input);
        assertEquals(expected, result);
    }

    private static java.util.stream.Stream<Arguments> provideSanitizeForPromptCases() {
        return java.util.stream.Stream.of(
                Arguments.of(null, ""),
                Arguments.of("Line1\r\nLine2", "Line1\nLine2"),
                Arguments.of("Line1\rLine2", "Line1\nLine2"),
                Arguments.of("Word1\tWord2", "Word1 Word2"),
                Arguments.of("Word1    Word2", "Word1 Word2"),
                Arguments.of("Word1\t\t\tWord2", "Word1 Word2"),
                Arguments.of("Word1 \t \t Word2", "Word1 Word2"),
                Arguments.of("Line1\n\n\nLine2", "Line1\n\nLine2"),
                Arguments.of("Line1\n\n\n\nLine2", "Line1\n\nLine2"),
                Arguments.of("Line1\n\nLine2", "Line1\n\nLine2"),
                Arguments.of("  Content  ", "Content"),
                Arguments.of("  Line1\r\n\t  Line2\r\t\tLine3\n\n\nLine4  ", "Line1\n Line2\n Line3\n\nLine4"),
                Arguments.of("   \t\t  \n\n  ", ""),
                Arguments.of("", ""),
                Arguments.of("\n\n\n", "")
        );
    }

    @ParameterizedTest
    @MethodSource("provideEscapeForFormatCases")
    void shouldEscapeForFormat(String input, String expected) {
        String result = promptSanitizer.escapeForFormat(input);
        assertEquals(expected, result);
    }

    private static java.util.stream.Stream<Arguments> provideEscapeForFormatCases() {
        return java.util.stream.Stream.of(
                Arguments.of(null, ""),
                Arguments.of("50% discount", "50%% discount"),
                Arguments.of("50% off and 25% more", "50%% off and 25%% more"),
                Arguments.of("Value {placeholder}", "Value {{placeholder}}"),
                Arguments.of("Price: {value} with 50% discount", "Price: {{value}} with 50%% discount"),
                Arguments.of("  Line1\r\n\t  Line2  ", "Line1\n Line2"),
                Arguments.of("  Price: {value} with 50% discount\r\n\t  ", "Price: {{value}} with 50%% discount"),
                Arguments.of("", ""),
                Arguments.of("\n\n\n", ""),
                Arguments.of("50%% discount", "50%%%% discount"),
                Arguments.of("{{value}}", "{{{{value}}}}")
        );
    }

    @Test
    void shouldEscapeClosingBrace() {
        String content = "Value }placeholder{";
        String result = promptSanitizer.escapeForFormat(content);
        assertEquals("Value }}placeholder{{", result);
    }
}
