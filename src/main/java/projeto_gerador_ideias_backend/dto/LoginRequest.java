package projeto_gerador_ideias_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dados para login do usuário")
public class LoginRequest {
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Schema(description = "Email do usuário", example = "joao.silva@example.com")
    private String email;
    
    @NotBlank(message = "Senha é obrigatória")
    @Schema(description = "Senha do usuário", example = "SenhaSegura@123")
    private String password;
}


