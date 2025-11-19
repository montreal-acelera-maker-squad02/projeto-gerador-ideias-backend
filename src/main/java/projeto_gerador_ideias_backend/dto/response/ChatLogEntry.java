package projeto_gerador_ideias_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatLogEntry {
    private Long messageId;
    private String role;
    private String content;
    private Integer tokensUsed;
    private String timestamp;
    private Long responseTimeMs;
    private Long sessionId;
    private String chatType; 
    private Long ideaId;
}



