package projeto_gerador_ideias_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumo de uma ideia para exibição no chat")
public class IdeaSummaryResponse {
    @Schema(description = "ID único da ideia", example = "10")
    private Long id;
    @Schema(description = "Resumo curto da ideia (máximo 5 palavras)", example = "Brasil: Biodiversidade e Cultura")
    private String summary;
    @Schema(description = "Tema da ideia", example = "tecnologia")
    private String theme;
    @Schema(description = "Data de criação", example = "2025-11-08T10:00:00")
    private String createdAt;
}

