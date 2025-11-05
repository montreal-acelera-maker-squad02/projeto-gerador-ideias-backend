package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.ErrorResponse;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ideia gerada com sucesso (ou rejeição de segurança retornada com sucesso)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdeaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Erro de validação (ex: contexto em branco ou tema nulo)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor (ex: falha ao conectar com o Ollama)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ideia aleatória gerada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdeaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor (ex: falha ao conectar com o Ollama)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico de ideias encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Nenhuma ideia encontrada no banco de dados"),
            @ApiResponse(responseCode = "400", description = "Erro de validação nos parâmetros de filtro"),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    })
    @GetMapping("/history")
    public ResponseEntity<?> getAllIdeas(
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


    // LISTAR IDEIAS DO USUARIO
    @Operation(
            summary = "Listar Ideias de um Usuário",
            description = "Retorna todas as ideias criadas por um usuário específico, ordenadas da mais recente para a mais antiga."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ideias do usuário encontradas com sucesso"),
            @ApiResponse(responseCode = "404", description = "Nenhuma ideia encontrada para o usuário informado"),
            @ApiResponse(responseCode = "400", description = "ID de usuário inválido"),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    })
    @GetMapping("/user/{userId}/ideas")
    public ResponseEntity<?> getIdeasByUser(@PathVariable Long userId) {
        try {
            List<IdeaResponse> ideias = ideaService.listarIdeiasPorUsuario(userId);
            return ResponseEntity.ok(ideias);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar ideias do usuário: " + e.getMessage());
        }
    }

}