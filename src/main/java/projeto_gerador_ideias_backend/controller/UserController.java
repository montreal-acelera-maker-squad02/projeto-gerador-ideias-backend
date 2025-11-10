package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.response.RegisterResponse;
import projeto_gerador_ideias_backend.dto.request.UpdateUserRequest;
import projeto_gerador_ideias_backend.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários")
public class UserController {
    
    private final UserService userService;
    
    @Operation(
        summary = "Atualizar dados do usuário",
        description = "Atualiza nome e/ou senha do usuário. Para alterar a senha, é necessário informar a senha antiga."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Usuário atualizado com sucesso"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Erro de validação"
    )
    @ApiResponse(
        responseCode = "401",
        description = "Senha antiga incorreta"
    )
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado"
    )
    @PutMapping("/{uuid}")
    public ResponseEntity<RegisterResponse> updateUser(
            @Parameter(description = "UUID único do usuário", required = true, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String uuid,
            @RequestBody UpdateUserRequest request) {
        RegisterResponse response = userService.updateUser(uuid, request);
        return ResponseEntity.ok(response);
    }
}

