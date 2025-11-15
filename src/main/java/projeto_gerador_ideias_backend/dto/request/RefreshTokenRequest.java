package projeto_gerador_ideias_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Requisição para renovar o access token usando refresh token")
public class RefreshTokenRequest {
    
    @NotBlank(message = "Refresh token é obrigatório")
    @Schema(description = "Refresh token para renovar o access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
}


