package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumo agregado dos logs de chat")
public class LogsSummary {
    @Schema(description = "Total de interações", example = "25")
    private Integer totalInteractions;
    @Schema(description = "Total de tokens de entrada", example = "375")
    private Integer totalTokensInput;
    @Schema(description = "Total de tokens de saída", example = "1125")
    private Integer totalTokensOutput;
    @Schema(description = "Total de tokens consumidos", example = "1500")
    private Integer totalTokens;
    @Schema(description = "Tempo médio de resposta em milissegundos", example = "1250.5")
    private Double averageResponseTimeMs;
}


