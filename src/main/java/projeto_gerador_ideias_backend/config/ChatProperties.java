package projeto_gerador_ideias_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chat")
@Getter
@Setter
public class ChatProperties {
    private int maxTokensPerMessage = 1000;
    private int maxCharsPerMessage = 1000;
    private int maxTokensPerChat = 10000;
    private int maxHistoryMessages = 3;
    private int maxInitialMessages = 10;
    private int ollamaTimeoutSeconds = 60;
    private int ollamaRetryAttempts = 3;
    private long ollamaRetryDelayMs = 1000;
    private int maxResponseLength = 100000;
    private int ollamaNumPredict = 300; 
    private double ollamaTemperature = 0.7; 
    private double ollamaTopP = 0.9;
    private int ollamaNumCtx = 2048; 
}

