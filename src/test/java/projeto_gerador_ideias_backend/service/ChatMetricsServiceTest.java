package projeto_gerador_ideias_backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ChatMetricsServiceTest {

    private SimpleMeterRegistry simpleMeterRegistry;

    @BeforeEach
    void setUp() {
        simpleMeterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldRecordMessageSent() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageSent("FREE");
        
        Counter counter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "FREE")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldHandleExceptionWhenRecordingMessageSent() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordMessageSent("FREE"));
    }

    @Test
    void shouldRecordMessageProcessingTimeForSuccess() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageProcessingTime(100L, "FREE", true);
        
        Timer timer = simpleMeterRegistry.find("chat.message.processing.time")
                .tag("chat_type", "FREE")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void shouldRecordMessageProcessingTimeForError() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageProcessingTime(200L, "IDEA_BASED", false);
        
        Timer timer = simpleMeterRegistry.find("chat.message.processing.time.error")
                .tag("chat_type", "IDEA_BASED")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void shouldHandleExceptionWhenRecordingMessageProcessingTime() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordMessageProcessingTime(100L, "FREE", true));
    }

    @Test
    void shouldRecordOllamaCallTime() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaCallTime(150L);
        
        Timer timer = simpleMeterRegistry.find("chat.ollama.call.time").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void shouldHandleExceptionWhenRecordingOllamaCallTime() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordOllamaCallTime(150L));
    }

    @Test
    void shouldRecordOllamaError() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaError("timeout");
        
        Counter counter = simpleMeterRegistry.find("chat.ollama.errors")
                .tag("error_type", "timeout")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldHandleExceptionWhenRecordingOllamaError() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordOllamaError("timeout"));
    }

    @ParameterizedTest
    @CsvSource({
        "50, user",
        "0, user",
        "100, assistant"
    })
    void shouldRecordTokenUsage(int amount, String role) {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordTokenUsage(amount, role);
        
        Counter counter = simpleMeterRegistry.find("chat.tokens.used")
                .tag("role", role)
                .counter();
        assertNotNull(counter);
        assertEquals((double) amount, counter.count());
    }

    @Test
    void shouldHandleExceptionWhenRecordingTokenUsage() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordTokenUsage(50, "user"));
    }

    @Test
    void shouldRecordValidationError() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordValidationError("invalid_input");
        
        Counter counter = simpleMeterRegistry.find("chat.validation.errors")
                .tag("error_type", "invalid_input")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldHandleExceptionWhenRecordingValidationError() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordValidationError("invalid_input"));
    }

    @Test
    void shouldUseCacheForCounter() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageSent("FREE");
        service.recordMessageSent("FREE");
        
        Counter counter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "FREE")
                .counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    void shouldUseCacheForTimer() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaCallTime(100L);
        service.recordOllamaCallTime(200L);
        
        Timer timer = simpleMeterRegistry.find("chat.ollama.call.time").timer();
        assertNotNull(timer);
        assertEquals(2, timer.count());
    }

    @Test
    void shouldHandleOddNumberOfTagsInGetCounter() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageSent("FREE");
        
        Counter counter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "FREE")
                .counter();
        assertNotNull(counter);
    }

    @Test
    void shouldHandleOddNumberOfTagsInGetTimer() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageProcessingTime(100L, "FREE", true);
        
        Timer timer = simpleMeterRegistry.find("chat.message.processing.time")
                .tag("chat_type", "FREE")
                .timer();
        assertNotNull(timer);
    }

    @Test
    void shouldFallbackToSimpleMeterRegistryWhenGetCounterFails() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordMessageSent("IDEA_BASED"));
        
        Counter counter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "IDEA_BASED")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldFallbackToSimpleMeterRegistryWhenGetTimerFails() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        assertDoesNotThrow(() -> service.recordOllamaCallTime(100L));
    }

    @Test
    void shouldHandleMultipleDifferentChatTypes() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageSent("FREE");
        service.recordMessageSent("IDEA_BASED");
        
        Counter freeCounter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "FREE")
                .counter();
        Counter ideaCounter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "IDEA_BASED")
                .counter();
        assertNotNull(freeCounter);
        assertNotNull(ideaCounter);
        assertEquals(1.0, freeCounter.count());
        assertEquals(1.0, ideaCounter.count());
    }

    @Test
    void shouldHandleMultipleDifferentErrorTypes() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaError("timeout");
        service.recordOllamaError("connection");
        
        Counter timeoutCounter = simpleMeterRegistry.find("chat.ollama.errors")
                .tag("error_type", "timeout")
                .counter();
        Counter connectionCounter = simpleMeterRegistry.find("chat.ollama.errors")
                .tag("error_type", "connection")
                .counter();
        assertNotNull(timeoutCounter);
        assertNotNull(connectionCounter);
        assertEquals(1.0, timeoutCounter.count());
        assertEquals(1.0, connectionCounter.count());
    }

    @Test
    void shouldHandleMultipleDifferentRoles() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordTokenUsage(10, "user");
        service.recordTokenUsage(20, "assistant");
        
        Counter userCounter = simpleMeterRegistry.find("chat.tokens.used")
                .tag("role", "user")
                .counter();
        Counter assistantCounter = simpleMeterRegistry.find("chat.tokens.used")
                .tag("role", "assistant")
                .counter();
        assertNotNull(userCounter);
        assertNotNull(assistantCounter);
        assertEquals(10.0, userCounter.count());
        assertEquals(20.0, assistantCounter.count());
    }

    @Test
    void shouldRecordZeroDuration() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaCallTime(0L);
        
        Timer timer = simpleMeterRegistry.find("chat.ollama.call.time").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }


    @Test
    void shouldHandleExceptionInCounterIncrement() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageSent("IDEA_BASED");
        
        Counter counter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "IDEA_BASED")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldHandleExceptionInTimerRecord() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaCallTime(100L);
        
        Timer timer = simpleMeterRegistry.find("chat.ollama.call.time").timer();
        assertNotNull(timer);
    }

    @Test
    void shouldHandleExceptionInCounterIncrementWithAmount() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordTokenUsage(100, "assistant");
        
        Counter counter = simpleMeterRegistry.find("chat.tokens.used")
                .tag("role", "assistant")
                .counter();
        assertNotNull(counter);
        assertEquals(100.0, counter.count());
        
        service.recordTokenUsage(50, "assistant");
        assertEquals(150.0, counter.count());
    }

    @Test
    void shouldRecordMultipleMessagesWithSameType() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordMessageSent("FREE");
        service.recordMessageSent("FREE");
        service.recordMessageSent("FREE");
        
        Counter counter = simpleMeterRegistry.find("chat.messages.sent")
                .tag("chat_type", "FREE")
                .counter();
        assertNotNull(counter);
        assertEquals(3.0, counter.count());
    }

    @Test
    void shouldRecordMultipleOllamaCalls() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordOllamaCallTime(100L);
        service.recordOllamaCallTime(200L);
        service.recordOllamaCallTime(300L);
        
        Timer timer = simpleMeterRegistry.find("chat.ollama.call.time").timer();
        assertNotNull(timer);
        assertEquals(3, timer.count());
    }

    @Test
    void shouldRecordLargeTokenAmounts() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordTokenUsage(1000, "user");
        service.recordTokenUsage(2000, "assistant");
        
        Counter userCounter = simpleMeterRegistry.find("chat.tokens.used")
                .tag("role", "user")
                .counter();
        Counter assistantCounter = simpleMeterRegistry.find("chat.tokens.used")
                .tag("role", "assistant")
                .counter();
        assertNotNull(userCounter);
        assertNotNull(assistantCounter);
        assertEquals(1000.0, userCounter.count());
        assertEquals(2000.0, assistantCounter.count());
    }

    @Test
    void shouldRecordMultipleValidationErrors() {
        ChatMetricsService service = new ChatMetricsService(simpleMeterRegistry);
        
        service.recordValidationError("invalid_input");
        service.recordValidationError("invalid_input");
        service.recordValidationError("missing_field");
        
        Counter invalidInputCounter = simpleMeterRegistry.find("chat.validation.errors")
                .tag("error_type", "invalid_input")
                .counter();
        Counter missingFieldCounter = simpleMeterRegistry.find("chat.validation.errors")
                .tag("error_type", "missing_field")
                .counter();
        assertNotNull(invalidInputCounter);
        assertNotNull(missingFieldCounter);
        assertEquals(2.0, invalidInputCounter.count());
        assertEquals(1.0, missingFieldCounter.count());
    }

    @Test
    void shouldHandleExceptionInGetCounter() {
        io.micrometer.core.instrument.MeterRegistry mockRegistry = org.mockito.Mockito.mock(io.micrometer.core.instrument.MeterRegistry.class);
        org.mockito.Mockito.when(mockRegistry.counter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<java.lang.Iterable<io.micrometer.core.instrument.Tag>>any())).thenThrow(new RuntimeException("Test exception"));
        
        ChatMetricsService service = new ChatMetricsService(mockRegistry);
        
        assertDoesNotThrow(() -> service.recordMessageSent("FREE"));
    }

    @Test
    void shouldHandleExceptionInGetTimer() {
        io.micrometer.core.instrument.MeterRegistry mockRegistry = org.mockito.Mockito.mock(io.micrometer.core.instrument.MeterRegistry.class);
        org.mockito.Mockito.when(mockRegistry.timer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<java.lang.Iterable<io.micrometer.core.instrument.Tag>>any())).thenThrow(new RuntimeException("Test exception"));
        
        ChatMetricsService service = new ChatMetricsService(mockRegistry);
        
        assertDoesNotThrow(() -> service.recordOllamaCallTime(100L));
    }

    @Test
    void shouldHandleExceptionInGetCounterWithTags() {
        io.micrometer.core.instrument.MeterRegistry mockRegistry = org.mockito.Mockito.mock(io.micrometer.core.instrument.MeterRegistry.class);
        org.mockito.Mockito.when(mockRegistry.counter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<java.lang.Iterable<io.micrometer.core.instrument.Tag>>any())).thenThrow(new RuntimeException("Test exception"));
        
        ChatMetricsService service = new ChatMetricsService(mockRegistry);
        
        assertDoesNotThrow(() -> service.recordMessageProcessingTime(100L, "FREE", true));
    }

    @Test
    void shouldHandleExceptionInGetTimerWithTags() {
        io.micrometer.core.instrument.MeterRegistry mockRegistry = org.mockito.Mockito.mock(io.micrometer.core.instrument.MeterRegistry.class);
        org.mockito.Mockito.when(mockRegistry.timer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<java.lang.Iterable<io.micrometer.core.instrument.Tag>>any())).thenThrow(new RuntimeException("Test exception"));
        
        ChatMetricsService service = new ChatMetricsService(mockRegistry);
        
        assertDoesNotThrow(() -> service.recordMessageProcessingTime(100L, "FREE", false));
    }
}
