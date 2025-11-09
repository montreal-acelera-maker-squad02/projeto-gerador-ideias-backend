package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.exceptions.TokenLimitExceededException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.model.ChatSession;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLimitValidator {

    private final ChatProperties chatProperties;
    private final TokenCalculationService tokenCalculationService;

    public int validateMessageLimitsAndGetTokens(String message) {
        if (message == null || message.isBlank()) {
            throw new ValidationException("Mensagem não pode ser vazia.");
        }

        int messageLength = message.length();
        int messageBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int maxChars = chatProperties.getMaxCharsPerMessage();
        
        if (messageLength > maxChars) {
            log.warn("Message exceeds character limit", Map.of(
                "messageLength", messageLength,
                "maxChars", maxChars
            ));
            throw new ValidationException(
                String.format("Sua mensagem excede o limite de %d caracteres (encontrados: %d). Por favor, encurte sua mensagem.", 
                    maxChars, messageLength)
            );
        }

        int maxBytes = chatProperties.getMaxCharsPerMessage() * 2;
        if (messageBytes > maxBytes) {
            throw new ValidationException(
                String.format("Sua mensagem excede o limite de tamanho (%d bytes). Por favor, encurte sua mensagem.", maxBytes)
            );
        }

        int messageTokens = tokenCalculationService.estimateTokens(message);
        if (messageTokens > chatProperties.getMaxTokensPerMessage()) {
            throw new ValidationException(
                String.format("Sua mensagem excede o limite de %d tokens. Por favor, encurte sua mensagem.", 
                    chatProperties.getMaxTokensPerMessage())
            );
        }
        
        return messageTokens;
    }

    public void validateChatNotBlocked(ChatSession session, int additionalTokens) {
        int currentChatTokens = tokenCalculationService.getTotalUserTokensInChat(session.getId());
        if (currentChatTokens + additionalTokens >= chatProperties.getMaxTokensPerChat()) {
            throw new TokenLimitExceededException(
                String.format("Este chat atingiu o limite de %d tokens. Por favor, inicie um novo chat.", 
                    chatProperties.getMaxTokensPerChat())
            );
        }
    }
    
    public void validateChatNotBlockedWithResponse(ChatSession session, int inputTokens, int outputTokens) {
        int currentChatTokens = tokenCalculationService.getTotalUserTokensInChat(session.getId());
        int totalNewTokens = inputTokens + outputTokens;
        if (currentChatTokens + totalNewTokens > chatProperties.getMaxTokensPerChat()) {
            throw new TokenLimitExceededException(
                String.format("Este chat atingiu o limite de %d tokens. Por favor, inicie um novo chat.", 
                    chatProperties.getMaxTokensPerChat())
            );
        }
    }

    public boolean isChatBlocked(ChatSession session) {
        int totalUserTokens = tokenCalculationService.getTotalUserTokensInChat(session.getId());
        return totalUserTokens >= chatProperties.getMaxTokensPerChat();
    }

    public void validateSessionNotBlocked(ChatSession session) {
        if (isChatBlocked(session)) {
            throw new TokenLimitExceededException(
                String.format("Este chat atingiu o limite de %d tokens. Por favor, inicie um novo chat.", 
                    chatProperties.getMaxTokensPerChat())
            );
        }
    }
}

