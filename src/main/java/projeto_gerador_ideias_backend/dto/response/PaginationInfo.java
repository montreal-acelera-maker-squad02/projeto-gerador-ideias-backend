package projeto_gerador_ideias_backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Informações de paginação")
public class PaginationInfo {
    @Schema(description = "Total de elementos", example = "100")
    private Long totalElements;
    @Schema(description = "Total de páginas", example = "10")
    private Integer totalPages;
    @Schema(description = "Página atual (1-based)", example = "1")
    private Integer currentPage;
    @Schema(description = "Indica se há próxima página", example = "true")
    private Boolean hasNext;
    @Schema(description = "Indica se há página anterior", example = "false")
    private Boolean hasPrevious;
}


