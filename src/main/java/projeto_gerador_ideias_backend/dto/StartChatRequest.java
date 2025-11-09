package projeto_gerador_ideias_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para iniciar uma sessão de chat")
public class StartChatRequest {
    @Positive(message = "O ID da ideia deve ser um número positivo")
    @Schema(description = "ID da ideia para iniciar chat baseado em ideia. Se null, inicia chat livre", example = "10", required = false)
    private Long ideaId;
}

