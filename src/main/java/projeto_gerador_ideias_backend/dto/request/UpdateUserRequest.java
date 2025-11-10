package projeto_gerador_ideias_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para atualização do usuário")
public class UpdateUserRequest {
    
    @NotBlank(message = "Nome é obrigatório")
    @Schema(description = "Novo nome do usuário", example = "João Silva Santos")
    private String name;
    
    @Schema(description = "Senha atual (obrigatória para alterar a senha)", example = "SenhaAtual@123")
    private String oldPassword;
    
    @Size(min = 8, max = 100, message = "Nova senha deve ter no mínimo 8 caracteres")
    @Schema(description = "Nova senha (comprimento mínimo 8, maiúscula, minúscula, dígito e caractere especial)", example = "NovaSenha@456")
    private String password;
    
    @Schema(description = "Confirmação da nova senha", example = "NovaSenha@456")
    private String confirmPassword;
}

