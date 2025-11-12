package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta paginada de mensagens antigas")
public class OlderMessagesResponse {
    @Schema(description = "Lista de mensagens antigas", required = true)
    private List<ChatMessageResponse> messages;
    
    @Schema(description = "Indica se há mais mensagens anteriores disponíveis", example = "true")
    private boolean hasMore;
}


