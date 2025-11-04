package projeto_gerador_ideias_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatMessageRequest {
    @NotBlank(message = "A mensagem não pode estar vazia")
    @Size(max = 1000, message = "A mensagem não pode ter mais de 1000 caracteres")
    private String message;
}

