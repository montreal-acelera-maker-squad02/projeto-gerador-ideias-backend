package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.response.ErrorResponse;
import projeto_gerador_ideias_backend.dto.request.IdeaRequest;
import projeto_gerador_ideias_backend.dto.response.IdeaResponse;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.service.IdeaService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;


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
    public ResponseEntity<IdeaResponse> generateIdea(
            @Valid @RequestBody IdeaRequest request,
            @Parameter(description = "Se 'true', ignora todos os caches (pessoal e técnico) e força uma nova chamada à IA.")
            @RequestParam(required = false, defaultValue = "false") boolean skipCache
    ) {
        IdeaResponse response = ideaService.generateIdea(request, skipCache);
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

    @Operation(
            summary = "Listar Histórico de Ideias (com filtro opcional por tema e data)",
            description = "Retorna as ideias salvas no banco de dados, podendo filtrar por tema e intervalo de datas. Ordenadas da mais recente para a mais antiga."
    )
    @ApiResponse(responseCode = "200", description = "Histórico de ideias encontrado com sucesso")
    @ApiResponse(responseCode = "404", description = "Nenhuma ideia encontrada no banco de dados")
    @ApiResponse(responseCode = "400", description = "Erro de validação nos parâmetros de filtro")
    @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    @GetMapping("/history")
    public ResponseEntity<List<IdeaResponse>> getAllIdeas(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long theme,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        List<IdeaResponse> ideias = ideaService.listarHistoricoIdeiasFiltrado(userId, theme, startDate, endDate);
        return ResponseEntity.ok(ideias);
    }

    @Operation(
            summary = "Listar Ideias de um Usuário",
            description = "Retorna todas as ideias criadas por um usuário específico, ordenadas da mais recente para a mais antiga."
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

    @GetMapping("/favorites")
    @Operation(summary = "Listar ideias favoritadas do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de ideias favoritadas retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado ou sem ideias favoritadas"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<IdeaResponse>> getFavoriteIdeas() {
        try {
            List<IdeaResponse> favoritas = ideaService.listarIdeiasFavoritadas();
            return ResponseEntity.ok(favoritas);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    @Operation(
            summary = "Obter Estatísticas de Geração",
            description = "Retorna estatísticas agregadas sobre a geração de ideias, como o tempo médio de resposta histórico de todas as ideias já geradas."
    )
    @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso")
    @GetMapping("/generation-stats")
    public ResponseEntity<Map<String, Object>> getIdeaStats() {
        Double averageTime = ideaService.getAverageIdeaGenerationTime();
        Map<String, Object> stats = Map.of(
                "averageGenerationTimeMs", averageTime != null ? averageTime : 0.0
        );
        return ResponseEntity.ok(stats);
    }

    @Operation(
            summary = "Obter contagem de ideias favoritas",
            description = "Retorna o número total de ideias favoritadas pelo usuário autenticado."
    )
    @ApiResponse(responseCode = "200", description = "Contagem retornada com sucesso")
    @GetMapping("/favorites/count")
    public ResponseEntity<Map<String, Long>> getFavoriteIdeasCount() {
        long count = ideaService.getFavoriteIdeasCount();
        return ResponseEntity.ok(Map.of("count", count));
    }
}
