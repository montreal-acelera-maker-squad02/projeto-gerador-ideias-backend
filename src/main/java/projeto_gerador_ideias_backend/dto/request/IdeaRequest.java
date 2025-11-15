package projeto_gerador_ideias_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados de entrada para gerar uma nova ideia")
public class IdeaRequest {

    @NotNull(message = "O tema não pode ser nulo")
    @Schema(description = "O tema principal da ideia", example = "tecnologia")
    private Long theme;

    @NotBlank(message = "O contexto não pode estar em branco")
    @Size(max = 50, message = "O contexto não pode exceder 50 caracteres")
    @Schema(description = "Um contexto ou tópico curto para a IA focar", example = "Ideia de slogan")
    private String context;
}
