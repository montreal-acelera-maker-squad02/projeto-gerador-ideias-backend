package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta contendo logs de chat agrupados por interações com informações do usuário (para admin)")
public class AdminChatLogsResponse {
    @Schema(description = "Data selecionada para os logs", example = "2025-11-08")
    private String selectedDate;
    
    @Schema(description = "ID do usuário filtrado (null se todos)", example = "1")
    private Long filteredUserId;
    
    @Schema(description = "Resumo agregado dos logs")
    private LogsSummary summary;
    
    @Schema(description = "Lista de interações (pares user + assistant) com informações do usuário")
    private List<AdminInteraction> interactions;
    
    @Schema(description = "Informações de paginação")
    private PaginationInfo pagination;
}

