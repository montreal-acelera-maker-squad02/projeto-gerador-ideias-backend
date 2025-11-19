package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.exceptions.ValidationException;

import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private static final Pattern MODERATION_DANGEROUS_PATTERN = 
        Pattern.compile("^\\s*\\[MODERACAO\\s*:\\s*PERIGOSO\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final String LOG_KEY_RESPONSE = "response";
    private static final String LOG_KEY_RESPONSE_LENGTH = "responseLength";

    public String validateAndNormalizeResponse(String aiResponse, boolean isFreeChat) {
        if (isFreeChat) {
            return handleFreeChatResponse(aiResponse);
        }
        
        return handleIdeaChatResponse(aiResponse);
    }
    
    private String handleFreeChatResponse(String aiResponse) {
        if (isDangerousContent(aiResponse)) {
            logInvalidResponse("Dangerous content detected in free chat, replacing with friendly message", aiResponse);
            return "Desculpe, não posso processar essa mensagem devido ao conteúdo. Posso ajudá-lo com outras questões?";
        }
        return aiResponse;
    }
    
    private String handleIdeaChatResponse(String aiResponse) {
        if (isDangerousContent(aiResponse) || isEmptyOrInvalidResponse(aiResponse)) {
            logInvalidResponse("Invalid response detected in idea chat, replacing with friendly message", aiResponse);
            return "Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia. Como posso ajudá-lo a desenvolver ou melhorar esta ideia?";
        }
        return aiResponse;
    }
    
    private void logInvalidResponse(String message, String aiResponse) {
        log.warn(message, Map.of(
            LOG_KEY_RESPONSE, aiResponse != null ? aiResponse : "null",
            LOG_KEY_RESPONSE_LENGTH, aiResponse != null ? aiResponse.length() : 0
        ));
    }
    
    /**
     * @deprecated Use validateAndNormalizeResponse em vez disso
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public void validateModerationResponse(String aiResponse, boolean isFreeChat) {
        if (isDangerousContent(aiResponse)) {
            if (isFreeChat) {
                throw new ValidationException("Desculpe, não posso processar essa mensagem devido ao conteúdo.");
            } else {
                throw new ValidationException("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia.");
            }
        } else if (!isFreeChat && isEmptyOrInvalidResponse(aiResponse)) {
            log.warn("Invalid response detected in idea chat", Map.of(
                LOG_KEY_RESPONSE, aiResponse != null ? aiResponse : "null",
                LOG_KEY_RESPONSE_LENGTH, aiResponse != null ? aiResponse.length() : 0
            ));
            throw new ValidationException("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia.");
        }
    }

    private boolean isDangerousContent(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }
        String trimmed = response.trim();
        
        return MODERATION_DANGEROUS_PATTERN.matcher(trimmed).find() ||
               trimmed.equalsIgnoreCase("[MODERACAO: PERIGOSO]") ||
               trimmed.startsWith("[MODERACAO: PERIGOSO]") ||
               trimmed.startsWith("[MODERAÇÃO: PERIGOSO]");
    }
    
    private boolean isEmptyOrInvalidResponse(String response) {
        if (response == null || response.isBlank()) {
            return true;
        }
        String trimmed = response.trim();
        
        if (trimmed.isEmpty()) {
            return true;
        }
        
        String withoutQuotes = trimmed.replace("\"", "").replace("'", "").trim();
        if (withoutQuotes.isEmpty()) {
            return true;
        }
        
        if (trimmed.length() <= 3 && !trimmed.matches("\\d+")) {
            String lower = trimmed.toLowerCase();
            if (lower.equals("não") || lower.equals("nao") || lower.equals("sim") || 
                lower.equals("ok") || lower.equals("sim") || lower.equals("não")) {
                return true;
            }
        }
        
        return false;
    }
}

