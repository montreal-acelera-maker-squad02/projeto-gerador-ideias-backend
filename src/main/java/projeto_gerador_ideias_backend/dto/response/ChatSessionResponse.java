package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta contendo os detalhes de uma sessão de chat")
public class ChatSessionResponse {
    @Schema(description = "ID único da sessão", example = "85")
    private Long sessionId;
    @Schema(description = "Tipo de chat (FREE ou IDEA_BASED)", example = "FREE")
    private String chatType;
    @Schema(description = "ID da ideia associada (null para chat livre)", example = "10")
    private Long ideaId;
    @Schema(description = "Resumo da ideia (apenas para chat IDEA_BASED)", example = "Brasil: Biodiversidade e Cultura")
    private String ideaSummary;
    @Schema(description = "Total de tokens das mensagens do usuário", example = "150")
    private Integer tokensInput;
    @Schema(description = "Total de tokens das respostas do assistente", example = "450")
    private Integer tokensOutput;
    @Schema(description = "Total de tokens consumidos (input + output)", example = "600")
    private Integer totalTokens;
    @Schema(description = "Tokens restantes no chat", example = "9400")
    private Integer tokensRemaining;
    @Schema(description = "Data e hora do último reset do chat", example = "2025-11-08T10:00:00")
    private String lastResetAt;
    @Schema(description = "Lista de mensagens da sessão")
    private List<ChatMessageResponse> messages;
    @Schema(description = "Indica se há mais mensagens antigas disponíveis para carregar", example = "true")
    private Boolean hasMoreMessages;
}


