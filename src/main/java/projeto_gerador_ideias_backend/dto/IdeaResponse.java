package projeto_gerador_ideias_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import projeto_gerador_ideias_backend.model.Idea;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class IdeaResponse {
    private Long id;
    private String theme;
    private String content;
    private LocalDateTime createdAt;
    private String modelUsed;
    private long executionTimeMs;
    private String userName;

    public IdeaResponse(Idea savedIdea) {
        this.id = savedIdea.getId();
        this.theme = savedIdea.getTheme().getValue();
        this.content = savedIdea.getGeneratedContent();
        this.createdAt = savedIdea.getCreatedAt();
        this.modelUsed = savedIdea.getModelUsed();
        this.executionTimeMs = savedIdea.getExecutionTimeMs();

        // Caso não haja usuário
        if (savedIdea.getUser() != null) {
            this.userName = savedIdea.getUser().getName();
        } else {
            this.userName = "Usuário não informado";
        }
    }
}