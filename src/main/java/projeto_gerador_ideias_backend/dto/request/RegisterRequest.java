package projeto_gerador_ideias_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para registro de um novo usuário")
public class RegisterRequest {
    
    @NotBlank(message = "Nome não pode estar vazio")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    @Schema(description = "Nome completo do usuário", example = "João Silva")
    private String name;
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Schema(description = "Email do usuário (único)", example = "joao.silva@example.com")
    private String email;
    
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 100, message = "Senha deve ter no mínimo 8 caracteres")
    @Schema(description = "Senha deve ter no mínimo 8 caracteres, conter maiúscula, minúscula, dígito e caractere especial", example = "SenhaSegura@123")
    private String password;
    
    @NotBlank(message = "Confirmação de senha é obrigatória")
    @Schema(description = "Confirmação da senha (deve ser igual à senha)", example = "SenhaSegura@123")
    private String confirmPassword;
}

