package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta contendo uma mensagem do chat")
public class ChatMessageResponse {
    @Schema(description = "ID único da mensagem", example = "123")
    private Long id;
    @Schema(description = "Papel da mensagem (user ou assistant)", example = "user")
    private String role;
    @Schema(description = "Conteúdo da mensagem", example = "Olá, como você está?")
    private String content;
    @Schema(description = "Tokens de entrada (mensagem do usuário)", example = "15")
    private Integer tokensInput;
    @Schema(description = "Tokens de saída (resposta do assistente)", example = "45")
    private Integer tokensOutput;
    @Schema(description = "Total de tokens desta interação", example = "60")
    private Integer totalTokens;
    @Schema(description = "Tokens restantes no chat após esta mensagem", example = "9940")
    private Integer tokensRemaining;
    @Schema(description = "Data e hora de criação da mensagem", example = "2025-11-08T13:00:00")
    private String createdAt;
}


