package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.ErrorResponse;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.service.IdeaService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;


    // GERAR NOVA IDEIA
    @Operation(
            summary = "Gerar Nova Ideia",
            description = "Gera uma nova ideia com base em um tema e contexto, utilizando a IA (Ollama). Inclui moderação de segurança."
    )
    @ApiResponse(responseCode = "200", description = "Ideia gerada com sucesso (ou rejeição de segurança retornada com sucesso)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdeaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Erro de validação (ex: contexto em branco ou tema nulo)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Erro interno no servidor (ex: falha ao conectar com o Ollama)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/generate")
    public ResponseEntity<IdeaResponse> generateIdea(@Valid @RequestBody IdeaRequest request) {
        IdeaResponse response = ideaService.generateIdea(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Gerar Ideia Aleatória (Surpreenda-me)",
            description = "Gera uma nova ideia totalmente aleatória (tema e tipo) pela IA, sem necessidade de enviar dados. Útil para o botão 'Surpreenda-me'."
    )
    @ApiResponse(responseCode = "200", description = "Ideia aleatória gerada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdeaResponse.class)))
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "500", description = "Erro interno no servidor (ex: falha ao conectar com o Ollama)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/surprise-me")
    public ResponseEntity<IdeaResponse> generateSurpriseIdea() {
        IdeaResponse response = ideaService.generateSurpriseIdea();
        return ResponseEntity.ok(response);
    }

    // LISTAR HISTORICO DE IDEIAS COM FILTROS

    @Operation(
            summary = "Listar Histórico de Ideias (com filtro opcional por tema e data)",
            description = "Retorna as ideias salvas no banco de dados, podendo filtrar por tema e intervalo de datas. Ordenadas da mais recente para a mais antiga."
    )
    @ApiResponse(responseCode = "200", description = "Histórico de ideias encontrado com sucesso")
    @ApiResponse(responseCode = "404", description = "Nenhuma ideia encontrada no banco de dados")
    @ApiResponse(responseCode = "400", description = "Erro de validação nos parâmetros de filtro")
    @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    @GetMapping("/history")
    public ResponseEntity<Object> getAllIdeas(
            @RequestParam(required = false) String theme,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        try {
            List<IdeaResponse> ideias = ideaService.listarHistoricoIdeiasFiltrado(theme, startDate, endDate);

            if (ideias == null || ideias.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Nenhuma ideia encontrada no banco de dados para os filtros informados.");
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


    // LISTAR MINHAS IDEIAS (Autenticado)
    @Operation(
            summary = "Listar Minhas Ideias",
            description = "Retorna todas as ideias criadas pelo usuário autenticado, ordenadas da mais recente para a mais antiga."
    )
    @ApiResponse(responseCode = "200", description = "Ideias do usuário encontradas com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "404", description = "Nenhuma ideia encontrada para o usuário autenticado")
    @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    @GetMapping("/my-ideas")
    public ResponseEntity<Object> getMyIdeas() {
        try {
            List<IdeaResponse> ideias = ideaService.listarMinhasIdeias();
            return ResponseEntity.ok(ideias);
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar ideias do usuário: " + e.getMessage());
        }
    }

    // FAVORITAR IDEIAS
    @Operation(summary = "Favoritar uma ideia")
    @ApiResponse(responseCode = "200", description = "Ideia favoritada com sucesso")
    @ApiResponse(responseCode = "400", description = "Erro de validação (Ideia já favoritada)")
    @ApiResponse(responseCode = "404", description = "Ideia não encontrada")
    @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    @PostMapping("/{ideaId}/favorite")
    public ResponseEntity<String> favoritarIdeia(@PathVariable Long ideaId) {
        try {
            ideaService.favoritarIdeia(ideaId);
            return ResponseEntity.ok("Ideia favoritada com sucesso.");
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("não encontrada")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao favoritar ideia: " + e.getMessage());
        }
    }


    // DESFAVORITAR IDEIAS
    @Operation(summary = "Remover ideia dos favoritos")
    @ApiResponse(responseCode = "200", description = "Ideia removida dos favoritos com sucesso")
    @ApiResponse(responseCode = "400", description = "Erro de validação (Ideia não favoritada)")
    @ApiResponse(responseCode = "404", description = "Ideia não encontrada")
    @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    @DeleteMapping("/{ideaId}/favorite")
    public ResponseEntity<String> desfavoritarIdeia(@PathVariable Long ideaId) {
        try {
            ideaService.desfavoritarIdeia(ideaId);
            return ResponseEntity.ok("Ideia removida dos favoritos com sucesso.");
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("não encontrada")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao remover ideia dos favoritos: " + e.getMessage());
        }
    }
}