package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Métricas de uma interação de chat")
public class InteractionMetrics {
    @Schema(description = "Tokens da mensagem do usuário", example = "15")
    private Integer tokensInput;
    @Schema(description = "Tokens da resposta do assistente", example = "45")
    private Integer tokensOutput;
    @Schema(description = "Total de tokens da interação", example = "60")
    private Integer totalTokens;
    @Schema(description = "Tempo de resposta em milissegundos", example = "1250")
    private Long responseTimeMs;
}


