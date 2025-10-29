package projeto_gerador_ideias_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import projeto_gerador_ideias_backend.model.Theme;

@Data
public class IdeaRequest {

    @NotNull(message = "O tema não pode ser nulo")
    private Theme theme;

    @NotBlank(message = "O contexto não pode estar em branco")
    @Size(max = 100, message = "O contexto não pode exceder 100 caracteres")
    private String context;
}