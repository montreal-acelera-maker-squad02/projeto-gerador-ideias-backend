package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.LoginRequest;
import projeto_gerador_ideias_backend.dto.LoginResponse;
import projeto_gerador_ideias_backend.dto.RegisterRequest;
import projeto_gerador_ideias_backend.dto.RegisterResponse;
import projeto_gerador_ideias_backend.dto.UpdateUserRequest;
import projeto_gerador_ideias_backend.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para gerenciamento e autenticação de usuários")
public class UserController {
    
    private final UserService userService;
    
    @Operation(
        summary = "Realizar login",
        description = "Autentica o usuário e retorna um token JWT"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Login realizado com sucesso"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Dados de entrada inválidos"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Credenciais inválidas"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Usuário não encontrado"
        )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Registrar novo usuário",
        description = "Cria um novo usuário no sistema com validação de senha forte"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Usuário criado com sucesso"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Erro de validação (senhas não coincidem, senha inválida, etc.)"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Email já está em uso"
        )
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @Operation(
        summary = "Atualizar dados do usuário",
        description = "Atualiza nome e/ou senha do usuário. Para alterar a senha, é necessário informar a senha antiga."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Usuário atualizado com sucesso"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Erro de validação"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Senha antiga incorreta"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Usuário não encontrado"
        )
    })
    @PutMapping("/{uuid}")
    public ResponseEntity<RegisterResponse> updateUser(
            @Parameter(description = "UUID único do usuário", required = true, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String uuid,
            @RequestBody UpdateUserRequest request) {
        RegisterResponse response = userService.updateUser(uuid, request);
        return ResponseEntity.ok(response);
    }
}
