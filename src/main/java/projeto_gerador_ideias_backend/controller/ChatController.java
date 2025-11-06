package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.*;
import projeto_gerador_ideias_backend.service.ChatService;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Endpoints para gerenciamento de chat com IA")
public class ChatController {

    private final ChatService chatService;

    @Operation(
            summary = "Iniciar sessão de chat",
            description = "Inicia uma nova sessão de chat. Pode ser vinculada a uma ideia específica (ideaId) ou livre (sem ideaId). Tokens são renovados a cada 24 horas."
    )
    @ApiResponse(responseCode = "200", description = "Sessão de chat iniciada ou recuperada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatSessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Erro de validação ou permissão",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Ideia não encontrada",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/start")
    public ResponseEntity<ChatSessionResponse> startChat(@Valid @RequestBody StartChatRequest request) {
        ChatSessionResponse response = chatService.startChat(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Enviar mensagem no chat",
            description = "Envia uma mensagem na sessão de chat. A mensagem é moderada para chat livre. Consome tokens do limite diário."
    )
    @ApiResponse(responseCode = "200", description = "Mensagem enviada e resposta da IA retornada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatMessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Limite de tokens atingido ou conteúdo inadequado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(sessionId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Obter histórico de ideias resumidas",
            description = "Retorna o histórico de ideias do usuário autenticado, cada uma resumida em 4 palavras para exibição rápida no chatbot."
    )
    @ApiResponse(responseCode = "200", description = "Lista de ideias resumidas retornada com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @GetMapping("/ideas/summary")
    public ResponseEntity<List<IdeaSummaryResponse>> getUserIdeasSummary() {
        List<IdeaSummaryResponse> response = chatService.getUserIdeasSummary();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Obter sessão de chat",
            description = "Retorna os detalhes de uma sessão de chat específica, incluindo todas as mensagens persistidas."
    )
    @ApiResponse(responseCode = "200", description = "Sessão retornada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatSessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Sem permissão para acessar esta sessão",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatSessionResponse> getSession(@PathVariable Long sessionId) {
        ChatSessionResponse response = chatService.getSession(sessionId);
        return ResponseEntity.ok(response);
    }
}

