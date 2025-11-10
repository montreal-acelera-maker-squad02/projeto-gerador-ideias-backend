package projeto_gerador_ideias_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Requisição para logout do usuário")
public class LogoutRequest {
    
    @Schema(description = "Refresh token para invalidar (opcional)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
}


