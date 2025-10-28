package projeto_gerador_ideias_backend.idea.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdeaResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
}
