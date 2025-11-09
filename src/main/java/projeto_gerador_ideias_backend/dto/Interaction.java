package projeto_gerador_ideias_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Interação completa entre usuário e assistente")
public class Interaction {
    @Schema(description = "ID único da interação", example = "1")
    private Long interactionId;
    @Schema(description = "Timestamp da interação", example = "2025-11-08T13:00:00")
    private String timestamp;
    @Schema(description = "ID da sessão de chat", example = "85")
    private Long sessionId;
    @Schema(description = "Tipo de chat (FREE ou IDEA_BASED)", example = "FREE")
    private String chatType;
    @Schema(description = "ID da ideia (null para chat livre)", example = "10")
    private Long ideaId;
    @Schema(description = "Mensagem do usuário", example = "Qual é a melhor forma de aprender programação?")
    private String userMessage;
    @Schema(description = "Resposta do assistente", example = "A melhor forma é praticar regularmente...")
    private String assistantMessage;
    @Schema(description = "Métricas da interação")
    private InteractionMetrics metrics;
}

