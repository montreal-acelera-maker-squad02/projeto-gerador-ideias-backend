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

    public String validateAndNormalizeResponse(String aiResponse, boolean isFreeChat) {
        if (isFreeChat && isDangerousContent(aiResponse)) {
            log.warn("Dangerous content detected in free chat, replacing with friendly message", Map.of(
                "response", aiResponse != null ? aiResponse : "null",
                "responseLength", aiResponse != null ? aiResponse.length() : 0
            ));
            return "Desculpe, não posso processar essa mensagem devido ao conteúdo. Posso ajudá-lo com outras questões?";
        }
        
        if (!isFreeChat) {
            if (isDangerousContent(aiResponse) || isEmptyOrInvalidResponse(aiResponse)) {
                log.warn("Invalid response detected in idea chat, replacing with friendly message", Map.of(
                    "response", aiResponse != null ? aiResponse : "null",
                    "responseLength", aiResponse != null ? aiResponse.length() : 0
                ));
                return "Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia. Como posso ajudá-lo a desenvolver ou melhorar esta ideia?";
            }
        }
        
        return aiResponse;
    }
    
    /**
     * @deprecated Use validateAndNormalizeResponse em vez disso
     */
    @Deprecated
    public void validateModerationResponse(String aiResponse, boolean isFreeChat) {
        if (isDangerousContent(aiResponse)) {
            if (isFreeChat) {
                throw new ValidationException("Desculpe, não posso processar essa mensagem devido ao conteúdo.");
            } else {
                throw new ValidationException("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia.");
            }
        }
        
        if (!isFreeChat && isEmptyOrInvalidResponse(aiResponse)) {
            log.warn("Invalid response detected in idea chat", Map.of(
                "response", aiResponse != null ? aiResponse : "null",
                "responseLength", aiResponse != null ? aiResponse.length() : 0
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

