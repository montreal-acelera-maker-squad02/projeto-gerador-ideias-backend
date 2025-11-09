package projeto_gerador_ideias_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Dados para envio de mensagem no chat")
public class ChatMessageRequest {
    @NotBlank(message = "A mensagem não pode estar vazia")
    @Size(max = 1000, message = "A mensagem não pode ter mais de 1000 caracteres")
    @Schema(description = "Mensagem do usuário", example = "Qual é a melhor forma de aprender programação?", required = true)
    private String message;
}

