package projeto_gerador_ideias_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import projeto_gerador_ideias_backend.model.Idea;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Schema(description = "Resposta contendo a ideia gerada pela IA")
public class IdeaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID único da ideia (se foi salva no histórico)", example = "10")
    private Long id;

    @Schema(description = "O tema principal da ideia", example = "tecnologia")
    private String theme;

    @Schema(description = "O texto da ideia gerado pela IA", example = "Inovação em todos os dias - Vive a revolução tecnológica")
    private String content;

    @Schema(description = "Data e hora da criação da ideia", example = "2025-10-30T15:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "O modelo de IA utilizado na geração", example = "mistral")
    private String modelUsed;

    @Schema(description = "Tempo que o servidor levou para processar a ideia (em milissegundos)", example = "1250")
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