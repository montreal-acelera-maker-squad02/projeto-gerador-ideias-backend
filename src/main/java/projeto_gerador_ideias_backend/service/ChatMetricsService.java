package projeto_gerador_ideias_backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMetricsService {

    private final MeterRegistry meterRegistry;
    private final Cache<String, Counter> counterCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    private final Cache<String, Timer> timerCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public void recordMessageSent(String chatType) {
        try {
            getCounter("chat.messages.sent", "chat_type", chatType).increment();
        } catch (Exception e) {
            log.warn("Failed to record message sent metric", e);
        }
    }

    public void recordMessageProcessingTime(long durationMs, String chatType, boolean success) {
        try {
            String timerName = success ? "chat.message.processing.time" : "chat.message.processing.time.error";
            getTimer(timerName, "chat_type", chatType)
                    .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to record message processing time metric", e);
        }
    }

    public void recordOllamaCallTime(long durationMs) {
        try {
            getTimer("chat.ollama.call.time").record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to record Ollama call time metric", e);
        }
    }

    public void recordOllamaError(String errorType) {
        try {
            getCounter("chat.ollama.errors", "error_type", errorType).increment();
        } catch (Exception e) {
            log.warn("Failed to record Ollama error metric", e);
        }
    }

    public void recordTokenUsage(int tokens, String role) {
        try {
            getCounter("chat.tokens.used", "role", role).increment(tokens);
        } catch (Exception e) {
            log.warn("Failed to record token usage metric", e);
        }
    }

    public void recordValidationError(String errorType) {
        try {
            getCounter("chat.validation.errors", "error_type", errorType).increment();
        } catch (Exception e) {
            log.warn("Failed to record validation error metric", e);
        }
    }

    private Counter getCounter(String name, String... tags) {
        try {
            String key = name + ":" + String.join(":", tags);
            return counterCache.get(key, k -> {
                Counter.Builder builder = Counter.builder(name);
                for (int i = 0; i < tags.length; i += 2) {
                    if (i + 1 < tags.length) {
                        builder.tag(tags[i], tags[i + 1]);
                    }
                }
                return builder.register(meterRegistry);
            });
        } catch (Exception e) {
            log.warn("Failed to get or create counter: " + name, e);
            return Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
    }

    private Timer getTimer(String name, String... tags) {
        try {
            String key = name + ":" + String.join(":", tags);
            return timerCache.get(key, k -> {
                Timer.Builder builder = Timer.builder(name);
                for (int i = 0; i < tags.length; i += 2) {
                    if (i + 1 < tags.length) {
                        builder.tag(tags[i], tags[i + 1]);
                    }
                }
                return builder.register(meterRegistry);
            });
        } catch (Exception e) {
            log.warn("Failed to get or create timer: " + name, e);
            return Timer.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
    }
}

