package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.request.ChatMessageRequest;
import projeto_gerador_ideias_backend.dto.request.StartChatRequest;
import projeto_gerador_ideias_backend.dto.response.ChatLogsResponse;
import projeto_gerador_ideias_backend.dto.response.AdminChatLogsResponse;
import projeto_gerador_ideias_backend.dto.response.ChatMessageResponse;
import projeto_gerador_ideias_backend.dto.response.ChatSessionResponse;
import projeto_gerador_ideias_backend.dto.response.ErrorResponse;
import projeto_gerador_ideias_backend.dto.response.IdeaSummaryResponse;
import projeto_gerador_ideias_backend.dto.response.OlderMessagesResponse;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.service.ChatService;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Endpoints para gerenciamento de chat com IA")
public class ChatController {
    
    private static final String UNKNOWN_IP = "unknown";
    private static final String LOCALHOST_IP = "127.0.0.1";
    
    private final ChatService chatService;
    private final projeto_gerador_ideias_backend.service.IdeasSummaryCacheService ideasSummaryCacheService;

    @Operation(
            summary = "Iniciar sessão de chat",
            description = "Inicia uma nova sessão de chat. Pode ser vinculada a uma ideia específica (ideaId) ou livre (sem ideaId). Cada chat tem limite de 10.000 tokens do usuário."
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
            description = "Envia uma mensagem na sessão de chat. A mensagem é moderada para chat livre. Limite de 1000 tokens e 1000 caracteres por mensagem do usuário."
    )
    @ApiResponse(responseCode = "200", description = "Mensagem enviada e resposta da IA retornada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatMessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Limite de tokens atingido ou conteúdo inadequado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Parameter(description = "ID da sessão de chat", required = true, example = "85")
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        String message = request.getMessage();
        if (message == null) {
            throw new ValidationException("A mensagem não pode ser nula.");
        }
        
        int messageLength = message.length();
        if (messageLength > 1000) {
            throw new ValidationException(
                String.format("Sua mensagem excede o limite de 1000 caracteres (encontrados: %d). Por favor, encurte sua mensagem.", 
                    messageLength)
            );
        }
        
        String clientIp = getClientIpAddress(httpRequest);
        log.debug("IP capturado no controller: {}", clientIp);
        ChatMessageResponse response = chatService.sendMessage(sessionId, request, clientIp);
        return ResponseEntity.ok(response);
    }
    
    private String getClientIpAddress(jakarta.servlet.http.HttpServletRequest request) {
        String ip = extractIpFromHeaders(request);
        
        if (!isValidIp(ip)) {
            ip = request.getRemoteAddr();
        }
        
        ip = normalizeIp(ip);
        
        if (!isValidIp(ip)) {
            String remoteAddr = request.getRemoteAddr();
            if (isValidIp(remoteAddr)) {
                ip = remoteAddr;
            } else {
                ip = LOCALHOST_IP;
            }
        }
        
        String finalIp = ip != null ? ip : LOCALHOST_IP;
        log.debug("IP capturado: {} (X-Forwarded-For: {}, X-Real-IP: {}, RemoteAddr: {})", 
            finalIp, 
            request.getHeader("X-Forwarded-For"),
            request.getHeader("X-Real-IP"),
            request.getRemoteAddr());
        return finalIp;
    }
    
    private String extractIpFromHeaders(jakarta.servlet.http.HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                return ip;
            }
        }
        return null;
    }
    
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN_IP.equalsIgnoreCase(ip);
    }
    
    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = LOCALHOST_IP;
        }
        
        return ip;
    }

    @Operation(
            summary = "Obter histórico de ideias resumidas",
            description = "Retorna o histórico de ideias do usuário autenticado, cada uma resumida em 4 palavras para exibição rápida no chatbot."
    )
    @ApiResponse(responseCode = "200", description = "Lista de ideias resumidas retornada com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @GetMapping("/ideas/summary")
    public ResponseEntity<List<IdeaSummaryResponse>> getUserIdeasSummary() {
        List<IdeaSummaryResponse> response = ideasSummaryCacheService.getUserIdeasSummary(
            chatService.getCurrentAuthenticatedUser().getId()
        );
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
    public ResponseEntity<ChatSessionResponse> getSession(
            @Parameter(description = "ID da sessão de chat", required = true, example = "85")
            @PathVariable Long sessionId) {
        ChatSessionResponse response = chatService.getSession(sessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Obter logs de chat",
            description = "Retorna todos os logs de mensagens (recebidas e enviadas pela IA) do usuário autenticado, agrupados por data. Filtra por data específica (padrão: hoje). Inclui tokens consumidos e tempo de resposta."
    )
    @ApiResponse(responseCode = "200", description = "Logs retornados com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatLogsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @GetMapping("/logs")
    public ResponseEntity<ChatLogsResponse> getChatLogs(
            @Parameter(description = "Data no formato YYYY-MM-DD (padrão: hoje)", example = "2025-11-08")
            @RequestParam(required = false) String date,
            @Parameter(description = "Número da página (padrão: 1)", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Tamanho da página (padrão: 10, máximo: 100)", example = "10")
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        ChatLogsResponse response = chatService.getChatLogs(date, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Obter logs de chat (Admin)",
            description = "Retorna todos os logs de mensagens de todos os usuários ou de um usuário específico. Requer permissão de administrador. Inclui informações do usuário (ID, nome, email) em cada interação."
    )
    @ApiResponse(responseCode = "200", description = "Logs retornados com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminChatLogsResponse.class)))
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Acesso negado. Apenas administradores podem acessar este endpoint.")
    @GetMapping("/admin/logs")
    public ResponseEntity<AdminChatLogsResponse> getAdminChatLogs(
            @Parameter(description = "Data no formato YYYY-MM-DD (padrão: hoje)", example = "2025-11-08")
            @RequestParam(required = false) String date,
            @Parameter(description = "ID do usuário para filtrar (opcional, null para todos)", example = "1")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "Número da página (padrão: 1)", example = "1")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Tamanho da página (padrão: 10, máximo: 100)", example = "10")
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        AdminChatLogsResponse response = chatService.getAdminChatLogs(date, userId, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Carregar mensagens antigas (paginação)",
            description = "Carrega mensagens anteriores a um timestamp específico. Usado para paginação estilo WhatsApp ao fazer scroll para cima. Retorna mensagens ordenadas do mais antigo para o mais recente e indica se há mais mensagens disponíveis."
    )
    @ApiResponse(responseCode = "200", description = "Mensagens retornadas com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OlderMessagesResponse.class)))
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Sessão não encontrada",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<OlderMessagesResponse> getOlderMessages(
            @Parameter(description = "ID da sessão de chat", required = true, example = "85")
            @PathVariable Long sessionId,
            @Parameter(description = "Timestamp ISO 8601 para buscar mensagens anteriores", required = true, example = "2025-11-08T13:00:00")
            @RequestParam String before,
            @Parameter(description = "Limite de mensagens (padrão: 20, máximo: 50)", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        OlderMessagesResponse response = chatService.getOlderMessages(sessionId, before, limit);
        return ResponseEntity.ok(response);
    }
}

