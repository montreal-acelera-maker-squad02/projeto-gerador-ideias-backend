package projeto_gerador_ideias_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserStatsResponse {
    private long generatedIdeasCount;
}