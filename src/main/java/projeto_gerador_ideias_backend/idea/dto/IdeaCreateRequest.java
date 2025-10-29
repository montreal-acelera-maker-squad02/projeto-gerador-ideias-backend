package projeto_gerador_ideias_backend.idea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdeaCreateRequest {
    @NotBlank(message = "O título não pode estar em branco.")
    @Size(min = 3, max = 100, message = "O título deve ter entre 3 e 100 caracteres.")
    private String title;

    @NotBlank(message = "A descrição não pode estar em branco.")
    private String description;

    @NotBlank(message = "A categoria não pode estar em branco.")
    private String category;
}