package projeto_gerador_ideias_backend.idea.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdeaResponse {
    private Long id; // Para simular o ID gerado pelo banco de dados
    private String title;
    private String description;
    private String category;
    // Você pode adicionar outros campos que a ideia terá
    // Ex: private LocalDateTime createdAt;
}