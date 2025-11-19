package projeto_gerador_ideias_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import projeto_gerador_ideias_backend.dto.request.OllamaRequest;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePreparationResult {
    private Long sessionId;
    private Long userId;
    private String userMessage;
    private int messageTokens;
    private String systemPrompt;
    private boolean isFreeChat;
    private List<OllamaRequest.Message> historyMessages;
}








