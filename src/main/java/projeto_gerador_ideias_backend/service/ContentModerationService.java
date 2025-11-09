package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.exceptions.ValidationException;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private static final Pattern MODERATION_DANGEROUS_PATTERN = 
        Pattern.compile("^\\s*\\[MODERACAO\\s*:\\s*PERIGOSO\\s*\\]", Pattern.CASE_INSENSITIVE);

    public void validateModerationResponse(String aiResponse, boolean isFreeChat) {
        if (isDangerousContent(aiResponse)) {
            if (isFreeChat) {
                throw new ValidationException("Desculpe, não posso processar essa mensagem devido ao conteúdo.");
            } else {
                throw new ValidationException("Desculpe, sua mensagem não está relacionada à ideia desta conversa. Por favor, mantenha o foco no tópico da ideia.");
            }
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
}

