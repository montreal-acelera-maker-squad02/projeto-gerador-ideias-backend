package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Objeto de resposta padrão para erros da API")
public class ErrorResponse {

    @Schema(description = "Um título curto para o erro", example = "Erro de validação")
    private String error;

    @Schema(description = "A mensagem detalhada do erro", example = "O contexto não pode estar em branco")
    private String message;
}

