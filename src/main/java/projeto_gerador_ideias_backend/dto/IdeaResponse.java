package projeto_gerador_ideias_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import projeto_gerador_ideias_backend.model.Idea;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class IdeaResponse {
    private String theme;
    private String content;
    private LocalDateTime createdAt;
    private String modelUsed;
    private long executionTimeMs;
    private Long id;

    public IdeaResponse(Idea savedIdea) {
        this.id = savedIdea.getId();
        this.theme = savedIdea.getTheme().getValue();
        this.content = savedIdea.getGeneratedContent();
        this.createdAt = savedIdea.getCreatedAt();
        this.modelUsed = savedIdea.getModelUsed();
        this.executionTimeMs = savedIdea.getExecutionTimeMs();
    }
}