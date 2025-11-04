package projeto_gerador_ideias_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.*;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.TokenLimitExceededException;
import projeto_gerador_ideias_backend.exceptions.ChatPermissionException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.model.*;
import projeto_gerador_ideias_backend.repository.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final int MAX_TOKENS_PER_DAY = 1000;
    private static final int MAX_HISTORY_TOKENS = 300;
    private static final int MAX_HISTORY_MESSAGES = 10;
    
    private static final String PROMPT_MODERACAO =
            "Analise o 'Tópico' abaixo. O tópico sugere uma intenção maliciosa, ilegal ou antiética (como phishing, fraude, malware, invasão, discurso de ódio, preconceito, etc.)?" +
                    "Responda APENAS 'SEGURO' ou 'PERIGOSO'.\n\n" +
                    "Tópico: \"%s\"\n\n" +
                    "RESPOSTA (SEGURO ou PERIGOSO):";

    private static final String PROMPT_CHAT_COM_IDEIA =
            "Você está conversando com um usuário sobre a seguinte ideia que foi gerada anteriormente:\n\n" +
                    "Ideia: \"%s\"\n\n" +
                    "Contexto original: \"%s\"\n\n" +
                    "Seja útil e criativo ao responder. Mantenha respostas concisas (máximo 100 palavras).";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final WebClient webClient;
    private final String ollamaBaseUrl;

    @Value("${ollama.model}")
    private String ollamaModel;

    public ChatService(ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository,
                       IdeaRepository ideaRepository,
                       UserRepository userRepository,
                       WebClient.Builder webClientBuilder,
                       @Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.webClient = webClientBuilder
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    @Transactional
    public ChatSessionResponse startChat(StartChatRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        ChatSession.ChatType chatType = request.getIdeaId() != null 
                ? ChatSession.ChatType.IDEA_BASED 
                : ChatSession.ChatType.FREE;

        ChatSession session;
        Idea idea = null;

        if (request.getIdeaId() != null) {
            idea = ideaRepository.findById(request.getIdeaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ideia não encontrada: " + request.getIdeaId()));
            
            if (!idea.getUser().getId().equals(currentUser.getId())) {
                throw new ChatPermissionException("Você não tem permissão para usar esta ideia.");
            }

            session = chatSessionRepository.findByUserIdAndIdeaId(currentUser.getId(), request.getIdeaId())
                    .orElse(null);
            
            if (session != null && session.getTokensUsed() >= MAX_TOKENS_PER_DAY) {
                throw new TokenLimitExceededException(
                    "Este chat já atingiu o limite de tokens. Você pode iniciar um novo chat com outra ideia após 24 horas."
                );
            }
        } else {
            session = chatSessionRepository.findByUserIdAndType(currentUser.getId(), ChatSession.ChatType.FREE)
                    .orElse(null);
            
            if (session != null) {
                long hoursSinceReset = ChronoUnit.HOURS.between(session.getLastResetAt(), LocalDateTime.now());
                
                if (session.getTokensUsed() >= MAX_TOKENS_PER_DAY) {
                    if (hoursSinceReset >= 24) {
                        session = null;
                    } else {
                        throw new TokenLimitExceededException(
                            "Limite de tokens atingido. Tente novamente em 24 horas para iniciar um novo chat livre."
                        );
                    }
                }
            }
        }

        if (session == null) {
            if (chatType == ChatSession.ChatType.IDEA_BASED && idea == null) {
                throw new IllegalArgumentException("Tipo de chat IDEA_BASED requer uma ideia válida.");
            }
            
            if (chatType == ChatSession.ChatType.IDEA_BASED) {
                List<ChatSession> blockedSessions = chatSessionRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
                boolean hasRecentlyBlocked = blockedSessions.stream()
                        .filter(s -> s.getType() == ChatSession.ChatType.IDEA_BASED)
                        .filter(s -> s.getTokensUsed() >= MAX_TOKENS_PER_DAY)
                        .anyMatch(s -> {
                            long hoursSinceLastReset = ChronoUnit.HOURS.between(s.getLastResetAt(), LocalDateTime.now());
                            return hoursSinceLastReset < 24;
                        });
                
                if (hasRecentlyBlocked) {
                    throw new TokenLimitExceededException(
                        "Você já atingiu o limite de tokens em um chat com ideia nas últimas 24 horas. Aguarde para iniciar um novo chat."
                    );
                }
            }
            
            session = new ChatSession(currentUser, chatType, idea);
            session = chatSessionRepository.save(session);
        } else {
            if (session.getIdea() != null) {
                session.getIdea().getId();
            }
        }

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        
        return buildSessionResponse(session, messages);
    }

    private int getTotalTokensUsedByUser(User user) {
        List<ChatSession> allSessions = chatSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        LocalDateTime now = LocalDateTime.now();
        
        return allSessions.stream()
                .filter(session -> {
                    long hoursSinceReset = ChronoUnit.HOURS.between(session.getLastResetAt(), now);
                    return hoursSinceReset < 24;
                })
                .mapToInt(ChatSession::getTokensUsed)
                .sum();
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long sessionId, ChatMessageRequest request) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessionId));

        User currentUser = getCurrentAuthenticatedUser();
        if (!session.getUser().getId().equals(currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para esta sessão.");
        }

        checkAndResetTokens(session);

        int totalTokensUsed = getTotalTokensUsedByUser(currentUser);
        if (totalTokensUsed >= MAX_TOKENS_PER_DAY) {
            throw new TokenLimitExceededException("Limite de tokens atingido. Tente novamente em 24 horas.");
        }

        int messageTokens = estimateTokens(request.getMessage());
        
        List<ChatMessage> previousMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        
        String contextPrompt = buildContextPrompt(session, previousMessages);
        String fullPrompt = contextPrompt + "\n\nUsuário: " + request.getMessage() + "\n\nAssistente:";
        
        int promptTokens = estimateTokens(fullPrompt);
        
        int moderationTokens = 0;
        if (session.getType() == ChatSession.ChatType.FREE) {
            String moderationPrompt = String.format(PROMPT_MODERACAO, request.getMessage());
            String moderationResult = callOllama(moderationPrompt, ollamaModel);
            if (moderationResult.contains("PERIGOSO")) {
                throw new ValidationException("Desculpe, não posso processar essa mensagem devido ao conteúdo.");
            }
            int moderationPromptTokens = estimateTokens(moderationPrompt);
            int moderationResponseTokens = estimateTokens(moderationResult);
            moderationTokens = moderationPromptTokens + moderationResponseTokens;
        }
        
        session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessionId));
        totalTokensUsed = getTotalTokensUsedByUser(currentUser);
        
        int estimatedTotalForMessage = moderationTokens + messageTokens + promptTokens + 50;
        if (totalTokensUsed + estimatedTotalForMessage > MAX_TOKENS_PER_DAY) {
            int tokensRemaining = MAX_TOKENS_PER_DAY - totalTokensUsed;
            throw new TokenLimitExceededException(
                String.format("Tokens insuficientes. Você tem %d tokens restantes, mas esta mensagem precisa de aproximadamente %d tokens (incluindo contexto e resposta da IA).", 
                    tokensRemaining, estimatedTotalForMessage)
            );
        }
        
        if (session.getType() == ChatSession.ChatType.FREE && moderationTokens > 0) {
            session.setTokensUsed(session.getTokensUsed() + moderationTokens);
            chatSessionRepository.save(session);
        }

        String aiResponse;
        int responseTokens;
        try {
            aiResponse = callOllama(fullPrompt, ollamaModel);
            responseTokens = estimateTokens(aiResponse);
            
            session = chatSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessionId));
            totalTokensUsed = getTotalTokensUsedByUser(currentUser);
            
            int totalTokensNeeded = promptTokens + responseTokens;
            if (totalTokensUsed + totalTokensNeeded > MAX_TOKENS_PER_DAY) {
                throw new TokenLimitExceededException("Limite de tokens atingido. Tente novamente em 24 horas.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar mensagem: " + e.getMessage(), e);
        }

        ChatMessage userMessage = new ChatMessage(session, ChatMessage.MessageRole.USER, request.getMessage(), messageTokens);
        chatMessageRepository.save(userMessage);
        
        session.setTokensUsed(session.getTokensUsed() + promptTokens + responseTokens);
        chatSessionRepository.save(session);

        ChatMessage assistantMessage = new ChatMessage(session, ChatMessage.MessageRole.ASSISTANT, aiResponse, responseTokens);
        chatMessageRepository.save(assistantMessage);

        int totalTokensForThisMessage = messageTokens + promptTokens + responseTokens + moderationTokens;
        
        int totalTokensUsedNow = getTotalTokensUsedByUser(currentUser);
        int tokensRemaining = MAX_TOKENS_PER_DAY - totalTokensUsedNow;

        return new ChatMessageResponse(
                assistantMessage.getId(),
                "assistant",
                assistantMessage.getContent(),
                totalTokensForThisMessage,
                Math.max(0, tokensRemaining),
                assistantMessage.getCreatedAt().toString()
        );
    }

    public List<IdeaSummaryResponse> getUserIdeasSummary() {
        User currentUser = getCurrentAuthenticatedUser();
        List<Idea> ideas = ideaRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        
        return ideas.stream().map(idea -> {
            String summary = summarizeIdeaSimple(idea.getGeneratedContent());
            return new IdeaSummaryResponse(
                    idea.getId(),
                    summary,
                    idea.getTheme().getValue(),
                    idea.getCreatedAt().toString()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessionId));

        User currentUser = getCurrentAuthenticatedUser();
        if (!session.getUser().getId().equals(currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para esta sessão.");
        }

        checkAndResetTokensIfNeeded(session);
        
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        
        return buildSessionResponse(session, messages);
    }

    private String summarizeIdeaSimple(String content) {
        String[] words = content.trim().split("\\s+");
        if (words.length > 4) {
            return String.join(" ", java.util.Arrays.copyOf(words, 4));
        }
        return content.trim();
    }

    private void checkAndResetTokens(ChatSession session) {
        long hoursSinceReset = ChronoUnit.HOURS.between(session.getLastResetAt(), LocalDateTime.now());
        if (hoursSinceReset >= 24) {
            session.setTokensUsed(0);
            session.setLastResetAt(LocalDateTime.now());
            chatSessionRepository.save(session);
        }
    }

    private void checkAndResetTokensIfNeeded(ChatSession session) {
        long hoursSinceReset = ChronoUnit.HOURS.between(session.getLastResetAt(), LocalDateTime.now());
        if (hoursSinceReset >= 24) {
        }
    }

    private String buildContextPrompt(ChatSession session, List<ChatMessage> previousMessages) {
        StringBuilder context = new StringBuilder();
        
        if (session.getType() == ChatSession.ChatType.IDEA_BASED && session.getIdea() != null) {
            session.getIdea().getId();
            Idea idea = session.getIdea();
            context.append(String.format(PROMPT_CHAT_COM_IDEIA, idea.getGeneratedContent(), idea.getContext()));
        } else {
            context.append("Você é um assistente útil e criativo. Responda de forma concisa e em português do Brasil.");
        }
        
        if (!previousMessages.isEmpty()) {
            context.append("\n\nHistórico da conversa:\n");
            
            int totalTokens = 0;
            List<ChatMessage> selectedMessages = new ArrayList<>();
            int startIndex = Math.max(0, previousMessages.size() - MAX_HISTORY_MESSAGES);
            
            for (int i = startIndex; i < previousMessages.size(); i++) {
                ChatMessage msg = previousMessages.get(i);
                int msgTokens = estimateTokens(msg.getContent());
                
                if (totalTokens + msgTokens > MAX_HISTORY_TOKENS) {
                    break;
                }
                
                selectedMessages.add(msg);
                totalTokens += msgTokens;
            }
            
            for (ChatMessage msg : selectedMessages) {
                String roleName = msg.getRole() == ChatMessage.MessageRole.USER ? "Usuário" : "Assistente";
                context.append(roleName).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        return context.toString();
    }

    private String callOllama(String prompt, String modelName) {
        OllamaRequest ollamaRequest = new OllamaRequest(modelName, prompt);
        try {
            OllamaResponse ollamaResponse = this.webClient.post()
                    .uri("/api/chat")
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                        response -> response.bodyToMono(String.class)
                            .map(body -> {
                                String errorMsg = String.format("Erro HTTP %d do Ollama: %s", 
                                    response.statusCode().value(), body);
                                return new RuntimeException(errorMsg);
                            }))
                    .bodyToMono(OllamaResponse.class)
                    .timeout(java.time.Duration.ofSeconds(60)) 
                    .block();

            if (ollamaResponse != null && ollamaResponse.getMessage() != null) {
                return ollamaResponse.getMessage().getContent().trim();
            } else {
                throw new RuntimeException("Resposta nula ou inválida do Ollama (/api/chat).");
            }
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String errorMessage = String.format(
                "Erro ao se comunicar com a IA (Ollama): %s %s. Verifique se o Ollama está rodando e se o modelo '%s' está disponível.",
                e.getStatusCode(), 
                e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : e.getMessage(),
                modelName
            );
            throw new RuntimeException(errorMessage, e);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException || 
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
                throw new RuntimeException("Timeout ao se comunicar com a IA (Ollama). Tente novamente.", e);
            }
            if (e.getMessage() != null && 
                (e.getMessage().contains("Connection refused") || 
                 e.getMessage().contains("Connection reset") ||
                 e.getMessage().contains("connect"))) {
                throw new RuntimeException(
                    String.format("Não foi possível conectar ao Ollama em %s. Verifique se o servidor está rodando.", 
                        ollamaBaseUrl), e);
            }
            throw new RuntimeException(
                String.format("Erro ao se comunicar com a IA (Ollama): %s. Modelo: %s", 
                    e.getMessage(), modelName), e);
        }
    }

    private int estimateTokens(String text) {
        return (text.length() + 3) / 4;
    }

    private ChatSessionResponse buildSessionResponse(ChatSession session, List<ChatMessage> messages) {
        String ideaSummary = null;
        if (session.getIdea() != null) {
            session.getIdea().getId();
            ideaSummary = summarizeIdeaSimple(session.getIdea().getGeneratedContent());
        }

        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(msg -> new ChatMessageResponse(
                        msg.getId(),
                        msg.getRole().name().toLowerCase(),
                        msg.getContent(),
                        msg.getTokensUsed(),
                        null,
                        msg.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        User user = session.getUser();
        int totalTokensUsed = getTotalTokensUsedByUser(user);
        int tokensRemaining = MAX_TOKENS_PER_DAY - totalTokensUsed;
        
        return new ChatSessionResponse(
                session.getId(),
                session.getType().name(),
                session.getIdea() != null ? session.getIdea().getId() : null,
                ideaSummary,
                session.getTokensUsed(),
                Math.max(0, tokensRemaining),
                session.getLastResetAt().toString(),
                messageResponses
        );
    }

    private User getCurrentAuthenticatedUser() {
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails)) {
            throw new ResourceNotFoundException("Usuário não autenticado. Não é possível usar o chat.");
        }

        String userEmail = ((org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal()).getUsername();

        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado no banco de dados: " + userEmail));
    }
}
