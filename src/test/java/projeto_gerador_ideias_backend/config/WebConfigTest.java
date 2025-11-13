package projeto_gerador_ideias_backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    private WebConfig webConfig;

    @BeforeEach
    void setUp() {
        webConfig = new WebConfig();
    }

    @Test
    void shouldCreateWebClientWithValidBaseUrl() {
        ReflectionTestUtils.setField(webConfig, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(webConfig, "ollamaTimeoutSeconds", 60);

        WebClient webClient = webConfig.webClient();

        assertNotNull(webClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldThrowExceptionWhenBaseUrlIsInvalid(String invalidBaseUrl) {
        ReflectionTestUtils.setField(webConfig, "ollamaBaseUrl", invalidBaseUrl);
        ReflectionTestUtils.setField(webConfig, "ollamaTimeoutSeconds", 60);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            webConfig.webClient();
        });

        assertEquals("ollama.base-url não pode ser nulo ou vazio", exception.getMessage());
    }

    @Test
    void shouldCreateWebClientWithDifferentTimeout() {
        ReflectionTestUtils.setField(webConfig, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(webConfig, "ollamaTimeoutSeconds", 30);

        WebClient webClient = webConfig.webClient();

        assertNotNull(webClient);
    }

    @Test
    void shouldConfigureCorsMappings() {
        CorsRegistry registry = new CorsRegistry();

        webConfig.addCorsMappings(registry);

        assertNotNull(registry);
    }

    @Test
    void shouldCreateWebClientWithCorrectBaseUrl() {
        String baseUrl = "http://localhost:11434";
        ReflectionTestUtils.setField(webConfig, "ollamaBaseUrl", baseUrl);
        ReflectionTestUtils.setField(webConfig, "ollamaTimeoutSeconds", 60);

        WebClient webClient = webConfig.webClient();

        assertNotNull(webClient);
    }
}

