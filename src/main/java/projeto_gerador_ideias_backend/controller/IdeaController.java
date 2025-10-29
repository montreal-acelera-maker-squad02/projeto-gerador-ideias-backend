package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.service.IdeaService;

import java.util.List;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;

    @PostMapping("/generate")
    public ResponseEntity<IdeaResponse> generateIdea(@Valid @RequestBody IdeaRequest request) {
        IdeaResponse response = ideaService.generateIdea(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Listar Histórico de Ideias",
            description = "Retorna todas as ideias salvas no banco de dados, ordenadas da mais recente para a mais antiga."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico de ideias encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Nenhuma ideia encontrada no banco de dados"),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    })
    @GetMapping("/history")
    public ResponseEntity<?> getAllIdeas() {
        try {
            List<IdeaResponse> ideias = ideaService.listarHistoricoIdeias();
            if (ideias == null || ideias.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Nenhuma ideia encontrada no banco de dados.");
            }
            return ResponseEntity.ok(ideias);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Erro de validação: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao buscar histórico de ideias: " + e.getMessage());
        }
    }
}