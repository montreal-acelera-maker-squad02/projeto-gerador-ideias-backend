package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.request.LoginRequest;
import projeto_gerador_ideias_backend.dto.response.LoginResponse;
import projeto_gerador_ideias_backend.dto.request.RegisterRequest;
import projeto_gerador_ideias_backend.dto.response.RegisterResponse;
import projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest;
import projeto_gerador_ideias_backend.dto.request.LogoutRequest;
import projeto_gerador_ideias_backend.dto.response.RefreshTokenResponse;
import projeto_gerador_ideias_backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para autenticação de usuários (login e registro)")
public class AuthController {
    
    private final UserService userService;
    
    @Operation(
        summary = "Realizar login",
        description = "Autentica o usuário e retorna um token JWT"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Login realizado com sucesso"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Dados de entrada inválidos"
    )
    @ApiResponse(
        responseCode = "401",
        description = "Credenciais inválidas"
    )
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado"
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Registrar novo usuário",
        description = "Cria um novo usuário no sistema com validação de senha forte"
    )
    @ApiResponse(
        responseCode = "201",
        description = "Usuário criado com sucesso"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Erro de validação (senhas não coincidem, senha inválida, etc.)"
    )
    @ApiResponse(
        responseCode = "409",
        description = "Email já está em uso"
    )
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @Operation(
        summary = "Renovar access token",
        description = "Renova o access token usando um refresh token válido. Retorna novos access e refresh tokens."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Tokens renovados com sucesso"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Refresh token inválido ou expirado"
    )
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = userService.refreshToken(request);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Realizar logout",
        description = "Invalida o access token atual e o refresh token (se fornecido). O access token é adicionado à blacklist."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Logout realizado com sucesso"
    )
    @ApiResponse(
        responseCode = "401",
        description = "Usuário não autenticado"
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest) {
        
        String accessToken = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        
        String refreshToken = request != null ? request.getRefreshToken() : null;
        userService.logout(accessToken, refreshToken);
        
        return ResponseEntity.ok().build();
    }
}


