package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.repository.ChatMessageRepository;

@Service
@RequiredArgsConstructor
public class TokenCalculationService {

    private final ChatMessageRepository chatMessageRepository;

    public int getTotalUserTokensInChat(Long sessionId) {
        int userTokens = chatMessageRepository.getTotalUserTokensBySessionId(
            sessionId, 
            projeto_gerador_ideias_backend.model.ChatMessage.MessageRole.USER
        );
        int assistantTokens = chatMessageRepository.getTotalUserTokensBySessionId(
            sessionId, 
            projeto_gerador_ideias_backend.model.ChatMessage.MessageRole.ASSISTANT
        );
        return userTokens + assistantTokens;
    }

    public int getTotalTokensUsedByUser(Long userId) {
        return chatMessageRepository.getTotalUserTokensByUserId(
            userId, 
            projeto_gerador_ideias_backend.model.ChatMessage.MessageRole.USER
        );
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        
        int charCount = normalized.length();
        int wordCount = countWords(normalized);
        
        int specialChars = countSpecialChars(normalized);
        
        double tokensByChars = charCount / 3.8;
        double tokensByWords = wordCount / 0.73;
        double tokensBySpecialChars = specialChars * 0.8;
        
        double estimatedTokens;
        
        if (wordCount > 10) {
            estimatedTokens = tokensByWords + tokensBySpecialChars;
        } else if (specialChars > charCount * 0.2) {
            estimatedTokens = tokensByChars;
        } else {
            estimatedTokens = (tokensByWords * 0.6) + (tokensByChars * 0.3) + (tokensBySpecialChars * 0.1);
        }
        
        return Math.max(1, (int) Math.ceil(estimatedTokens));
    }
    
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        String[] parts = text.trim().split("\\s+");
        int count = 0;
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }
    
    private int countSpecialChars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                count++;
            }
        }
        return count;
    }
}
