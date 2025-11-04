package projeto_gerador_ideias_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdeaSummaryResponse {
    private Long id;
    private String summary;
    private String theme;
    private String createdAt;
}

