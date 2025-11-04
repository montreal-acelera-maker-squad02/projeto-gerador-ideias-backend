package projeto_gerador_ideias_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {
    private Long sessionId;
    private String chatType;
    private Long ideaId;
    private String ideaSummary;
    private Integer tokensUsed;
    private Integer tokensRemaining;
    private String lastResetAt;
    private List<ChatMessageResponse> messages;
}

