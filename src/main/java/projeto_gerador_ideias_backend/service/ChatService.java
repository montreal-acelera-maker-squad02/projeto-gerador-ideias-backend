package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.dto.*;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.TokenLimitExceededException;
import projeto_gerador_ideias_backend.exceptions.ChatPermissionException;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import jakarta.persistence.OptimisticLockException;
import projeto_gerador_ideias_backend.model.*;
import projeto_gerador_ideias_backend.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String ERROR_SESSION_NOT_FOUND = "Sessão não encontrada: ";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final ChatProperties chatProperties;
    private final TokenCalculationService tokenCalculationService;
    private final PromptBuilderService promptBuilderService;
    private final OllamaIntegrationService ollamaIntegrationService;
    private final ChatLimitValidator chatLimitValidator;
    private final ContentModerationService contentModerationService;
    private final UserCacheService userCacheService;
    private final ChatMetricsService chatMetricsService;

    @Transactional
    public ChatSessionResponse startChat(StartChatRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        Long ideaId = request.getIdeaId();
        Idea idea = ideaId != null ? validateAndGetIdea(ideaId, currentUser) : null;
        ChatSession session = findOrCreateSession(currentUser, ideaId, idea);
        
        log.info("Chat session started", Map.of(
            "sessionId", session.getId(),
            "chatType", session.getType().name(),
            "ideaId", ideaId != null ? ideaId : "null"
        ));
        
        List<ChatMessage> messages = getInitialMessages(session.getId());
        return buildSessionResponse(session, messages);
    }

    private Idea validateAndGetIdea(Long ideaId, User currentUser) {
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ideia não encontrada: " + ideaId));
        
        if (!java.util.Objects.equals(idea.getUser().getId(), currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para usar esta ideia.");
        }
        
        return idea;
    }

    private ChatSession findOrCreateSession(User currentUser, Long ideaId, Idea idea) {
        ChatSession session = ideaId != null 
                ? findExistingIdeaBasedSession(currentUser, ideaId)
                : findExistingFreeSession(currentUser);
        
        if (session == null) {
            session = createNewSession(currentUser, ideaId != null 
                    ? ChatSession.ChatType.IDEA_BASED 
                    : ChatSession.ChatType.FREE, idea);
        }
        
        return session;
    }

    private ChatSession findExistingIdeaBasedSession(User currentUser, Long ideaId) {
        ChatSession session = chatSessionRepository.findByUserIdAndIdeaId(currentUser.getId(), ideaId)
                .orElse(null);
        
        if (session == null) {
            return null;
        }
        
        chatLimitValidator.validateSessionNotBlocked(session);
        
        ensureIdeaContextCacheAndSave(session);
        
        return session;
    }
    
    private void ensureIdeaContextCache(ChatSession session) {
        if (session.getType() == ChatSession.ChatType.IDEA_BASED && 
            (session.getCachedIdeaContent() == null || session.getCachedIdeaContext() == null)) {
            if (session.getIdea() != null) {
                session.setCachedIdeaContent(session.getIdea().getGeneratedContent());
                session.setCachedIdeaContext(session.getIdea().getContext());
                log.debug("Populated idea context cache in memory", Map.of(
                    "sessionId", session.getId(),
                    "ideaId", session.getIdea().getId()
                ));
            }
        }
    }
    
    private void ensureIdeaContextCacheAndSave(ChatSession session) {
        if (session.getType() == ChatSession.ChatType.IDEA_BASED && 
            (session.getCachedIdeaContent() == null || session.getCachedIdeaContext() == null)) {
            if (session.getIdea() != null) {
                session.setCachedIdeaContent(session.getIdea().getGeneratedContent());
                session.setCachedIdeaContext(session.getIdea().getContext());
                chatSessionRepository.save(session);
                log.debug("Populated and saved idea context cache", Map.of(
                    "sessionId", session.getId(),
                    "ideaId", session.getIdea().getId()
                ));
            }
        }
    }

    private ChatSession findExistingFreeSession(User currentUser) {
        ChatSession session = chatSessionRepository.findByUserIdAndType(currentUser.getId(), ChatSession.ChatType.FREE)
                .orElse(null);
        
        if (session == null) {
            return null;
        }
        
        if (chatLimitValidator.isChatBlocked(session)) {
            return null;
        }
        
        return session;
    }

    private ChatSession createNewSession(User currentUser, ChatSession.ChatType chatType, Idea idea) {
        if (currentUser.getId() == null) {
            throw new ResourceNotFoundException("Usuário não possui ID válido");
        }
        ChatSession session = new ChatSession(currentUser, chatType, idea);
        return chatSessionRepository.save(session);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long sessionId, ChatMessageRequest request) {
        long startTime = System.currentTimeMillis();
        String chatType = "UNKNOWN";
        
        try {
            MessagePreparationResult preparation = prepareMessage(sessionId, request);
            chatType = preparation.isFreeChat() ? "FREE" : "IDEA_BASED";
            
            String aiResponse;
            int responseTokens;
            try {
                List<projeto_gerador_ideias_backend.dto.OllamaRequest.Message> historyMessages = 
                    preparation.getHistoryMessages();
                
                if (historyMessages == null || historyMessages.isEmpty()) {
                    aiResponse = ollamaIntegrationService.callOllamaWithSystemPrompt(
                        preparation.getSystemPrompt(), 
                        preparation.getUserMessage()
                    );
                } else {
                    aiResponse = ollamaIntegrationService.callOllamaWithHistory(
                        preparation.getSystemPrompt(),
                        historyMessages,
                        preparation.getUserMessage()
                    );
                }
                contentModerationService.validateModerationResponse(
                    aiResponse, 
                    preparation.isFreeChat()
                );
                responseTokens = tokenCalculationService.estimateTokens(aiResponse);
                
                if (responseTokens < 0) {
                    log.warn("Negative token count calculated, setting to 0", Map.of(
                        "sessionId", sessionId,
                        "responseTokens", responseTokens
                    ));
                    responseTokens = 0;
                }
            } catch (ValidationException e) {
                log.error("Validation error during message processing", Map.of("sessionId", sessionId), e);
                chatMetricsService.recordValidationError("moderation");
                throw e;
            } catch (OllamaServiceException e) {
                log.error("Ollama service error", Map.of("sessionId", sessionId), e);
                chatMetricsService.recordOllamaError("service_error");
                throw e;
            } catch (OptimisticLockException e) {
                log.warn("Optimistic lock conflict during Ollama call", Map.of("sessionId", sessionId), e);
                throw new TokenLimitExceededException("Sessão foi atualizada por outra requisição. Tente novamente.");
            } catch (Exception e) {
                log.error("Unexpected error during Ollama call", Map.of("sessionId", sessionId, "errorType", e.getClass().getName()), e);
                throw new OllamaServiceException("Erro ao comunicar com a IA: " + e.getMessage(), e);
            }

            ChatMessageResponse response = saveMessageAndResponse(sessionId, preparation, aiResponse, responseTokens);
            
            long duration = System.currentTimeMillis() - startTime;
            chatMetricsService.recordMessageSent(chatType);
            chatMetricsService.recordMessageProcessingTime(duration, chatType, true);
            chatMetricsService.recordTokenUsage(preparation.getMessageTokens(), "USER");
            chatMetricsService.recordTokenUsage(responseTokens, "ASSISTANT");
            
            return response;
        } catch (ValidationException e) {
            chatMetricsService.recordValidationError("message_limits");
            throw e;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (ChatPermissionException e) {
            throw e;
        } catch (TokenLimitExceededException e) {
            throw e;
        } catch (InvalidDataAccessApiUsageException e) {
            log.error("Transaction error in sendMessage", Map.of("sessionId", sessionId), e);
            throw new OllamaServiceException("Erro ao processar mensagem. Tente novamente.", e);
        } catch (DataAccessException e) {
            log.error("Database access error in sendMessage", Map.of("sessionId", sessionId), e);
            throw new OllamaServiceException("Erro ao acessar o banco de dados. Tente novamente.", e);
        } catch (OllamaServiceException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            chatMetricsService.recordMessageProcessingTime(duration, chatType, false);
            log.error("Unexpected error in sendMessage", Map.of("sessionId", sessionId, "errorType", e.getClass().getName()), e);
            throw new OllamaServiceException("Erro inesperado ao processar mensagem: " + e.getMessage(), e);
        }
    }

    @Transactional
    private MessagePreparationResult prepareMessage(Long sessionId, ChatMessageRequest request) {
        ChatSession session;
        try {
            session = chatSessionRepository.findByIdWithLock(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception in prepareMessage, retrying without lock", Map.of("sessionId", sessionId), e);
            session = chatSessionRepository.findByIdWithIdea(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        }

        User currentUser = getCurrentAuthenticatedUser();
        if (!java.util.Objects.equals(session.getUser().getId(), currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para esta sessão.");
        }

        String userMessage = request.getMessage();
        
        if (userMessage == null || userMessage.isBlank()) {
            throw new ValidationException("Mensagem não pode estar vazia.");
        }
        
        int messageLength = userMessage.length();
        int maxChars = chatProperties.getMaxCharsPerMessage();
        if (messageLength > maxChars) {
            log.warn("Message exceeds character limit", Map.of(
                "messageLength", messageLength,
                "maxChars", maxChars
            ));
            throw new ValidationException(
                String.format("Sua mensagem excede o limite de %d caracteres (encontrados: %d). Por favor, encurte sua mensagem.", 
                    maxChars, messageLength)
            );
        }
        
        int messageTokens = chatLimitValidator.validateMessageLimitsAndGetTokens(userMessage);
        chatLimitValidator.validateChatNotBlocked(session, messageTokens);

        ensureIdeaContextCache(session);

        List<ChatMessage> previousMessages = getRecentMessages(session.getId());
        
        String systemPrompt;
        if (session.getType() == ChatSession.ChatType.FREE) {
            systemPrompt = promptBuilderService.buildSystemPromptForFreeChat();
        } else {
            systemPrompt = promptBuilderService.buildSystemPromptForIdeaChat(session);
        }
        
        List<projeto_gerador_ideias_backend.dto.OllamaRequest.Message> historyMessages = 
            promptBuilderService.buildMessageHistory(previousMessages);

        if (!historyMessages.isEmpty()) {
            log.debug("Message history for session", Map.of(
                "sessionId", sessionId,
                "historySize", historyMessages.size(),
                "historyRoles", historyMessages.stream()
                    .map(m -> m.getRole())
                    .collect(java.util.stream.Collectors.joining(", "))
            ));
        }

        log.info("Processing message", Map.of(
            "sessionId", sessionId,
            "messageTokens", messageTokens,
            "chatType", session.getType().name(),
            "historyMessagesCount", historyMessages.size(),
            "userMessage", userMessage.substring(0, Math.min(50, userMessage.length()))
        ));

        return new MessagePreparationResult(
            sessionId,
            currentUser.getId(),
            userMessage,
            messageTokens,
            systemPrompt,
            session.getType() == ChatSession.ChatType.FREE,
            historyMessages
        );
    }

    @Transactional
    private ChatMessageResponse saveMessageAndResponse(
            Long sessionId, 
            MessagePreparationResult preparation, 
            String aiResponse, 
            int responseTokens) {
        
        ChatSession session;
        try {
            session = chatSessionRepository.findByIdWithLock(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception in saveMessageAndResponse, retrying without lock", Map.of("sessionId", sessionId), e);
            session = chatSessionRepository.findByIdWithIdea(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        }
        
        try {
            int tokensInputThisMessage = preparation.getMessageTokens();
            int tokensOutputThisMessage = responseTokens;
            int totalTokensThisMessage = tokensInputThisMessage + tokensOutputThisMessage;
            
            chatLimitValidator.validateChatNotBlocked(session, tokensInputThisMessage);
            chatLimitValidator.validateChatNotBlockedWithResponse(session, tokensInputThisMessage, tokensOutputThisMessage);

            ChatMessage userMessageEntity = new ChatMessage(
                session, 
                ChatMessage.MessageRole.USER, 
                preparation.getUserMessage(), 
                preparation.getMessageTokens()
            );
            chatMessageRepository.save(userMessageEntity);
            chatMessageRepository.flush();

            String cleanedResponse = removeModerationTags(aiResponse);
            
            if (cleanedResponse == null || cleanedResponse.isBlank()) {
                log.warn("Response is empty after cleaning moderation tags", Map.of("sessionId", sessionId));
                throw new OllamaServiceException("Resposta da IA está vazia após processamento.");
            }
            
            int previousTokensRemaining = chatProperties.getMaxTokensPerChat();
            
            List<ChatMessage> allAssistantMessages = chatMessageRepository.findUserMessagesBySessionId(
                sessionId, 
                ChatMessage.MessageRole.ASSISTANT
            );
            
            ChatMessage lastAssistantMessageWithTokens = null;
            for (int i = allAssistantMessages.size() - 1; i >= 0; i--) {
                ChatMessage msg = allAssistantMessages.get(i);
                if (msg.getTokensRemaining() != null) {
                    lastAssistantMessageWithTokens = msg;
                    break;
                }
            }
            
            log.debug("Search for previous assistant message", Map.of(
                "sessionId", sessionId,
                "totalAssistantMessages", allAssistantMessages.size(),
                "foundMessageWithTokens", lastAssistantMessageWithTokens != null
            ));
            
            if (lastAssistantMessageWithTokens != null) {
                previousTokensRemaining = lastAssistantMessageWithTokens.getTokensRemaining();
                log.info("Using previous tokensRemaining", Map.of(
                    "previousTokensRemaining", previousTokensRemaining,
                    "lastAssistantMessageId", lastAssistantMessageWithTokens.getId(),
                    "lastAssistantMessageCreatedAt", lastAssistantMessageWithTokens.getCreatedAt()
                ));
                    } else {
                log.info("No previous assistant messages with tokensRemaining found, using initial value", Map.of(
                    "initialTokensRemaining", previousTokensRemaining,
                    "sessionId", sessionId
                ));
            }
            
            int tokensRemaining = Math.max(0, previousTokensRemaining - totalTokensThisMessage);
            
            log.debug("Token calculation", Map.of(
                "sessionId", sessionId,
                "previousTokensRemaining", previousTokensRemaining,
                "tokensInputThisMessage", tokensInputThisMessage,
                "tokensOutputThisMessage", tokensOutputThisMessage,
                "totalTokensThisMessage", totalTokensThisMessage,
                "calculatedTokensRemaining", tokensRemaining
            ));
            
            ChatMessage assistantMessage = new ChatMessage(
                session, 
                ChatMessage.MessageRole.ASSISTANT, 
                cleanedResponse, 
                responseTokens,
                tokensRemaining
            );
            chatMessageRepository.save(assistantMessage);
            
            chatMessageRepository.flush();
            
            log.debug("Assistant message saved with tokensRemaining", Map.of(
                "assistantMessageId", assistantMessage.getId(),
                "tokensRemainingSaved", assistantMessage.getTokensRemaining(),
                "expectedTokensRemaining", tokensRemaining
            ));
            
            int totalUserTokensAccumulated = chatMessageRepository.getTotalUserTokensBySessionId(
                sessionId, 
                ChatMessage.MessageRole.USER
            );
            int totalAssistantTokensAccumulated = chatMessageRepository.getTotalUserTokensBySessionId(
                sessionId, 
                ChatMessage.MessageRole.ASSISTANT
            );
            int totalTokensAccumulated = totalUserTokensAccumulated + totalAssistantTokensAccumulated;
            
            log.debug("Token calculation after save", Map.of(
                "sessionId", sessionId,
                "tokensInputThisMessage", tokensInputThisMessage,
                "tokensOutputThisMessage", tokensOutputThisMessage,
                "totalTokensThisMessage", totalTokensThisMessage,
                "totalTokensAccumulated", totalTokensAccumulated,
                "previousTokensRemaining", previousTokensRemaining,
                "tokensRemaining", tokensRemaining
            ));

            log.info("Message processed successfully", Map.of(
                "sessionId", sessionId,
                "userMessageTokens", preparation.getMessageTokens(),
                "assistantMessageTokens", responseTokens,
                "totalUserTokensAccumulated", totalUserTokensAccumulated,
                "totalAssistantTokensAccumulated", totalAssistantTokensAccumulated,
                "totalTokensAccumulated", totalTokensAccumulated,
                "tokensRemaining", tokensRemaining
            ));

        return new ChatMessageResponse(
                assistantMessage.getId(),
                "assistant",
                assistantMessage.getContent(),
                    tokensInputThisMessage,
                    tokensOutputThisMessage,
                    totalTokensThisMessage,
                tokensRemaining,
                assistantMessage.getCreatedAt().toString()
        );
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock conflict", Map.of("sessionId", sessionId), e);
            throw new TokenLimitExceededException("Sessão foi atualizada por outra requisição. Tente novamente.");
        } catch (ResourceNotFoundException e) {
            log.warn("Session not found during save", Map.of("sessionId", sessionId), e);
            throw e;
        }
    }

    private List<ChatMessage> getInitialMessages(Long sessionId) {
        int limit = chatProperties.getMaxInitialMessages();
        
        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesOptimized(sessionId, limit);
        
        if (messages.size() > limit) {
            return messages.subList(messages.size() - limit, messages.size());
        }
        
        return messages;
    }

    private List<ChatMessage> getRecentMessages(Long sessionId) {
        int limit = chatProperties.getMaxHistoryMessages();
        
        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesOptimized(sessionId, limit);
        
        if (messages.size() > limit) {
            return messages.subList(messages.size() - limit, messages.size());
        }
        
        return messages;
    }
    
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getOlderMessages(Long sessionId, String beforeTimestamp, Integer limit) {
        ChatSession session = chatSessionRepository.findByIdWithIdea(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        
        User currentUser = getCurrentAuthenticatedUser();
        if (!java.util.Objects.equals(session.getUser().getId(), currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para esta sessão.");
        }
        
        LocalDateTime before;
        try {
            before = LocalDateTime.parse(beforeTimestamp);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ValidationException("Formato de timestamp inválido. Use ISO 8601 (ex: 2025-11-08T13:00:00)");
        }
        
        int messageLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 20;
        
        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(0, messageLimit);
        
        List<ChatMessage> messages = chatMessageRepository.findMessagesBeforeTimestamp(
            sessionId, 
            before, 
            pageable
        );
        
        messages.sort((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));
        
        return messages.stream()
            .map(msg -> {
                int accumulatedUserTokens = 0;
                int accumulatedAssistantTokens = 0;
                
                List<ChatMessage> allMessagesUpToThis = 
                    chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                        .stream()
                        .filter(m -> m.getCreatedAt().isBefore(msg.getCreatedAt()) || 
                                    m.getCreatedAt().equals(msg.getCreatedAt()))
                        .collect(Collectors.toList());
                
                for (ChatMessage m : allMessagesUpToThis) {
                    if (m.getRole() == ChatMessage.MessageRole.USER) {
                        accumulatedUserTokens += m.getTokensUsed();
                    } else {
                        accumulatedAssistantTokens += m.getTokensUsed();
                    }
                }
                
                int totalTokens = accumulatedUserTokens + accumulatedAssistantTokens;
                
                int tokensRemainingForMessage = chatProperties.getMaxTokensPerChat();
                if (msg.getRole() == ChatMessage.MessageRole.ASSISTANT && msg.getTokensRemaining() != null) {
                    tokensRemainingForMessage = msg.getTokensRemaining();
                } else if (msg.getRole() == ChatMessage.MessageRole.USER) {
                    int msgIndex = allMessagesUpToThis.indexOf(msg);
                    if (msgIndex >= 0 && msgIndex + 1 < allMessagesUpToThis.size()) {
                        ChatMessage nextMsg = allMessagesUpToThis.get(msgIndex + 1);
                        if (nextMsg.getRole() == ChatMessage.MessageRole.ASSISTANT && 
                            nextMsg.getTokensRemaining() != null) {
                            int tokensUsedBetween = msg.getTokensUsed() + nextMsg.getTokensUsed();
                            tokensRemainingForMessage = nextMsg.getTokensRemaining() + tokensUsedBetween;
                        }
                    }
                }
                
                return new ChatMessageResponse(
                    msg.getId(),
                    msg.getRole().name().toLowerCase(),
                    msg.getContent(),
                    accumulatedUserTokens,
                    accumulatedAssistantTokens,
                    totalTokens,
                    tokensRemainingForMessage,
                    msg.getCreatedAt().toString()
                );
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
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
        }).toList();
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdWithIdea(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));

        User currentUser = getCurrentAuthenticatedUser();
        if (!java.util.Objects.equals(session.getUser().getId(), currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para esta sessão.");
        }
        
        ensureIdeaContextCache(session);
        
        List<ChatMessage> messages = getInitialMessages(session.getId());
        return buildSessionResponse(session, messages);
    }

    @Transactional(readOnly = true)
    public ChatLogsResponse getChatLogs(String dateStr, Integer page, Integer size) {
        User currentUser = getCurrentAuthenticatedUser();
        
        LocalDate targetDate;
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                targetDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (java.time.format.DateTimeParseException e) {
                throw new ValidationException("Formato de data inválido. Use YYYY-MM-DD (exemplo: 2024-01-15).");
            }
        } else {
            targetDate = LocalDate.now();
        }
        
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (size != null && size > 0) ? Math.min(size, 100) : 10;
        
        LocalDateTime startDate = targetDate.atStartOfDay();
        LocalDateTime endDate = targetDate.plusDays(1).atStartOfDay();
        
        List<ChatMessage> allMessages = chatMessageRepository.findByUserIdAndDateRange(
            currentUser.getId(), startDate, endDate);
        
        List<Interaction> allInteractions = transformMessagesToInteractions(allMessages);
        
        int totalInteractions = allInteractions.size();
        int totalPages = (int) Math.ceil((double) totalInteractions / pageSize);
        
        int startIndex = pageNumber * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalInteractions);
        
        List<Interaction> paginatedInteractions = new ArrayList<>();
        if (startIndex < totalInteractions) {
            paginatedInteractions = allInteractions.subList(startIndex, endIndex);
        }
        
        LogsSummary summary = calculateSummary(allInteractions);
        
        PaginationInfo pagination = new PaginationInfo(
                (long) totalInteractions,
                totalPages,
                pageNumber + 1,
                pageNumber + 1 < totalPages,
                pageNumber > 0
        );
        
        return new ChatLogsResponse(
                targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                summary,
                paginatedInteractions,
                pagination
        );
    }
    
    private List<Interaction> transformMessagesToInteractions(List<ChatMessage> messages) {
        List<Interaction> interactions = new ArrayList<>();
        long interactionId = 1;
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            
            if (message == null) {
                continue;
            }
            
            ChatSession session = message.getSession();
            if (session == null) {
                log.warn("Message without session", Map.of("messageId", message.getId()));
                continue;
            }
            
            if (message.getRole() == ChatMessage.MessageRole.USER) {
                String userMessage = message.getContent();
                int tokensInput = message.getTokensUsed();
                String timestamp = message.getCreatedAt().toString();
                
                ChatMessage assistantMessage = null;
                if (i + 1 < messages.size()) {
                    ChatMessage nextMessage = messages.get(i + 1);
                    if (nextMessage != null && 
                        nextMessage.getRole() == ChatMessage.MessageRole.ASSISTANT &&
                        nextMessage.getSession() != null &&
                        nextMessage.getSession().getId().equals(session.getId())) {
                        assistantMessage = nextMessage;
                    }
                }
                
                if (assistantMessage != null) {
                    String assistantContent = assistantMessage.getContent();
                    int tokensOutput = assistantMessage.getTokensUsed();
                    int totalTokens = tokensInput + tokensOutput;
                    
                    Long responseTimeMs = null;
                    if (assistantMessage.getCreatedAt() != null && message.getCreatedAt() != null) {
                        responseTimeMs = java.time.Duration.between(
                                message.getCreatedAt(),
                                assistantMessage.getCreatedAt()
                        ).toMillis();
                    }
                    
                    InteractionMetrics metrics = new InteractionMetrics(
                            tokensInput,
                            tokensOutput,
                            totalTokens,
                            responseTimeMs
                    );
                    
                    Interaction interaction = new Interaction(
                            interactionId++,
                            timestamp,
                            session.getId(),
                            session.getType().name(),
                            session.getIdea() != null ? session.getIdea().getId() : null,
                            userMessage,
                            assistantContent,
                            metrics
                    );
                    
                    interactions.add(interaction);
                    i++;
                } else {
                    InteractionMetrics metrics = new InteractionMetrics(
                            tokensInput,
                            0,
                            tokensInput,
                            null
                    );
                    
                    Interaction interaction = new Interaction(
                            interactionId++,
                            timestamp,
                            session.getId(),
                            session.getType().name(),
                            session.getIdea() != null ? session.getIdea().getId() : null,
                            userMessage,
                            null,
                            metrics
                    );
                    
                    interactions.add(interaction);
                }
            }
        }
        
        return interactions;
    }
    
    private LogsSummary calculateSummary(List<Interaction> interactions) {
        if (interactions.isEmpty()) {
            return new LogsSummary(0, 0, 0, 0, null);
        }
        
        int totalInteractions = interactions.size();
        int totalTokensInput = 0;
        int totalTokensOutput = 0;
        long totalResponseTime = 0;
        int interactionsWithResponseTime = 0;
        
        for (Interaction interaction : interactions) {
            if (interaction.getMetrics() != null) {
                totalTokensInput += interaction.getMetrics().getTokensInput() != null 
                        ? interaction.getMetrics().getTokensInput() : 0;
                totalTokensOutput += interaction.getMetrics().getTokensOutput() != null 
                        ? interaction.getMetrics().getTokensOutput() : 0;
                
                if (interaction.getMetrics().getResponseTimeMs() != null) {
                    totalResponseTime += interaction.getMetrics().getResponseTimeMs();
                    interactionsWithResponseTime++;
                }
            }
        }
        
        int totalTokens = totalTokensInput + totalTokensOutput;
        Double averageResponseTimeMs = interactionsWithResponseTime > 0 
                ? (double) totalResponseTime / interactionsWithResponseTime 
                : null;
        
        return new LogsSummary(
                totalInteractions,
                totalTokensInput,
                totalTokensOutput,
                totalTokens,
                averageResponseTimeMs
        );
    }
    
    private List<ChatLogEntry> processMessagesToLogs(List<ChatMessage> messages) {
        List<ChatLogEntry> logEntries = new ArrayList<>();
        ChatMessage previousUserMessage = null;
        
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            
            ChatSession session = message.getSession();
            if (session == null) {
                log.warn("Message without session", Map.of("messageId", message.getId()));
                continue;
            }
            
            Long responseTimeMs = null;
            
            if (message.getRole() == ChatMessage.MessageRole.ASSISTANT && previousUserMessage != null) {
                long diffMillis = java.time.Duration.between(
                        previousUserMessage.getCreatedAt(),
                        message.getCreatedAt()
                ).toMillis();
                responseTimeMs = diffMillis;
            }
            
            ChatLogEntry logEntry = new ChatLogEntry(
                    message.getId(),
                    message.getRole().name().toLowerCase(),
                    message.getContent(),
                    message.getTokensUsed(),
                    message.getCreatedAt().toString(),
                    responseTimeMs,
                    session.getId(),
                    session.getType().name(),
                    session.getIdea() != null ? session.getIdea().getId() : null
            );
            
            logEntries.add(logEntry);
            
            if (message.getRole() == ChatMessage.MessageRole.USER) {
                previousUserMessage = message;
            } else {
                previousUserMessage = null;
            }
        }
        
        return logEntries;
    }

    private String summarizeIdeaSimple(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        String trimmed = content.trim();
        
        String[] words = trimmed.split("\\s+");
        if (words.length <= 10) {
            return trimmed;
        }
        
        try {
            String systemPrompt = """
                Você é um assistente especializado em criar títulos curtos, completos e descritivos.
                Crie títulos concisos que capturem a essência do conteúdo em português do Brasil.
                O título deve ser uma frase completa e fazer sentido, nunca cortado no meio.
                """;
            
            String userPrompt = String.format(
                "Crie um título curto e completo (máximo 5 palavras) para esta ideia: %s\n\n" +
                "REGRAS OBRIGATÓRIAS:\n" +
                "- Título deve ter EXATAMENTE 5 palavras ou menos\n" +
                "- O título deve ser uma frase COMPLETA que faz sentido\n" +
                "- NUNCA corte no meio de uma palavra ou frase\n" +
                "- Seja direto e descritivo\n" +
                "- Responda APENAS o título, sem explicações, saudações, formatação ou pontuação final\n" +
                "- Não use aspas, dois pontos ou pontuação desnecessária\n" +
                "- O título deve fazer sentido completo, não pode terminar com preposições ou artigos soltos",
                trimmed
            );
            
            String generatedTitle = ollamaIntegrationService.callOllamaWithSystemPrompt(systemPrompt, userPrompt);
            
            String cleanedTitle = generatedTitle.trim()
                .replaceAll("^[\"']+|[\"']+$", "")
                .replaceAll("^Título[:\\s]+", "")
                .replaceAll("^Titulo[:\\s]+", "")
                .replaceAll("[:;,\\.!?]+$", "")
                .trim();
            
            String[] titleWords = cleanedTitle.split("\\s+");
            if (titleWords.length > 5) {
                cleanedTitle = cleanTitleToMaxWords(cleanedTitle, 5);
            }
            
            cleanedTitle = ensureTitleCompleteness(cleanedTitle);
            
            if (cleanedTitle != null && !cleanedTitle.isBlank() && cleanedTitle.length() > 3) {
                log.debug("Generated title using Ollama", Map.of(
                    "originalLength", trimmed.length(),
                    "titleLength", cleanedTitle.length(),
                    "title", cleanedTitle
                ));
                return cleanedTitle;
            }
        } catch (Exception e) {
            log.warn("Failed to generate title with Ollama, using fallback", Map.of(
                "error", e.getMessage()
            ), e);
        }
        
        return summarizeIdeaSimpleFallback(trimmed, words);
    }
    
    private String cleanTitleToMaxWords(String title, int maxWords) {
        String[] words = title.split("\\s+");
        if (words.length <= maxWords) {
            return title;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) {
                result.append(" ");
            }
            result.append(words[i]);
        }
        return result.toString().trim();
    }
    
    private String ensureTitleCompleteness(String title) {
        if (title == null || title.isBlank()) {
            return title;
        }
        
        String[] words = title.split("\\s+");
        if (words.length == 0) {
            return title;
        }
        
        String[] incompleteWords = {"e", "a", "o", "os", "as", "de", "do", "da", "dos", "das",
                                    "em", "no", "na", "nos", "nas", "com", "para", "por", "que",
                                    "mais", "menos", "maior", "menor", "melhor", "pior"};
        
        String lastWord = words[words.length - 1].toLowerCase().replaceAll("[,;.!?:]+$", "");
        
        for (String incomplete : incompleteWords) {
            if (lastWord.equals(incomplete) && words.length > 1) {
                String[] newWords = new String[words.length - 1];
                System.arraycopy(words, 0, newWords, 0, words.length - 1);
                return String.join(" ", newWords).trim();
            }
        }
        
        return title;
    }
    
    private String summarizeIdeaSimpleFallback(String trimmed, String[] words) {
        int maxWords = 5;
        
        for (int i = 0; i < Math.min(words.length, maxWords); i++) {
            String word = words[i];
            if (word.matches(".*[,;]+$")) {
        StringBuilder summary = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    if (j > 0) {
                        summary.append(" ");
                    }
                    String cleanWord = words[j].replaceAll("[,;]+$", "");
                    summary.append(cleanWord);
                }
                String result = summary.toString().trim();
                String[] resultWords = result.split("\\s+");
                if (resultWords.length > maxWords) {
                    StringBuilder limited = new StringBuilder();
                    for (int k = 0; k < maxWords; k++) {
                        if (k > 0) {
                            limited.append(" ");
                        }
                        limited.append(resultWords[k]);
                    }
                    result = limited.toString().trim();
                }
                return result;
            }
        }
        
        for (int i = 0; i < Math.min(words.length, maxWords); i++) {
            String word = words[i];
            if (word.matches(".*[.!?]+$")) {
                StringBuilder summary = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    if (j > 0) {
                        summary.append(" ");
                    }
                    summary.append(words[j]);
                }
                String result = summary.toString().trim();
                String[] resultWords = result.split("\\s+");
                if (resultWords.length > maxWords) {
                    StringBuilder limited = new StringBuilder();
                    for (int k = 0; k < maxWords; k++) {
                        if (k > 0) {
                            limited.append(" ");
                        }
                        limited.append(resultWords[k]);
                    }
                    result = limited.toString().trim();
                }
                return result;
            }
        }
        
        int targetWords = Math.min(maxWords, words.length);
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < targetWords; i++) {
            if (i > 0) {
                summary.append(" ");
            }
            summary.append(words[i]);
        }
        
        String result = summary.toString().trim();
        result = result.replaceAll("\\s*[:;,\\-—–]+\\s*$", "");
        
        String[] resultWords = result.split("\\s+");
        String[] incompleteWords = {"mais", "menos", "maior", "menor", "melhor", "pior",
                                    "com", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
                                    "a", "o", "os", "as", "ao", "à", "aos", "às", "para", "por", "que"};
        
        while (resultWords.length > 1 && resultWords.length <= maxWords) {
            String lastWord = resultWords[resultWords.length - 1].toLowerCase().replaceAll("[,;.!?]+$", "");
            boolean found = false;
            for (String incomplete : incompleteWords) {
                if (lastWord.equals(incomplete)) {
                    String[] newWords = new String[resultWords.length - 1];
                    System.arraycopy(resultWords, 0, newWords, 0, resultWords.length - 1);
                    resultWords = newWords;
                    found = true;
                    break;
                }
            }
            if (!found) {
                break;
            }
        }
        
        if (resultWords.length > maxWords) {
            String[] limited = new String[maxWords];
            System.arraycopy(resultWords, 0, limited, 0, maxWords);
            resultWords = limited;
        }
        
        if (resultWords.length > 0) {
            StringBuilder finalResult = new StringBuilder();
            for (int i = 0; i < resultWords.length; i++) {
                if (i > 0) {
                    finalResult.append(" ");
                }
                finalResult.append(resultWords[i]);
            }
            return finalResult.toString().trim();
        }
        
            if (words.length >= 3) {
            int wordsToTake = Math.min(3, maxWords);
            StringBuilder finalResult = new StringBuilder();
            for (int i = 0; i < wordsToTake; i++) {
                if (i > 0) {
                    finalResult.append(" ");
                }
                finalResult.append(words[i]);
            }
            return finalResult.toString().trim();
        }
        
        return trimmed;
    }

    private ChatSessionResponse buildSessionResponse(ChatSession session, List<ChatMessage> messages) {
        String ideaSummary = null;
        if (session.getIdea() != null) {
            ideaSummary = summarizeIdeaSimple(session.getIdea().getGeneratedContent());
        }

        List<ChatMessageResponse> messageResponses = new java.util.ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);

            int tokensInputThisMsg = 0;
            int tokensOutputThisMsg = 0;
            int tokensRemainingForMessage = chatProperties.getMaxTokensPerChat();

            if (msg.getRole() == ChatMessage.MessageRole.USER) {
                tokensInputThisMsg = msg.getTokensUsed();

                if (i + 1 < messages.size()) {
                    ChatMessage nextMsg = messages.get(i + 1);
                    if (nextMsg.getRole() == ChatMessage.MessageRole.ASSISTANT) {
                        tokensOutputThisMsg = nextMsg.getTokensUsed();
                        if (nextMsg.getTokensRemaining() != null) {
                            tokensRemainingForMessage = nextMsg.getTokensRemaining() + tokensInputThisMsg + tokensOutputThisMsg;
                        }
                    }
                }
            } else {
                if (i > 0) {
                    ChatMessage prevMsg = messages.get(i - 1);
                    if (prevMsg.getRole() == ChatMessage.MessageRole.USER) {
                        tokensInputThisMsg = prevMsg.getTokensUsed();
                    }
                }
                tokensOutputThisMsg = msg.getTokensUsed();

                if (msg.getTokensRemaining() != null) {
                    tokensRemainingForMessage = msg.getTokensRemaining();
                }
            }

            int totalTokensThisMsg = tokensInputThisMsg + tokensOutputThisMsg;
            
            messageResponses.add(new ChatMessageResponse(
                    msg.getId(),
                    msg.getRole().name().toLowerCase(),
                    msg.getContent(),
                    tokensInputThisMsg,
                    tokensOutputThisMsg,
                    totalTokensThisMsg,
                    tokensRemainingForMessage,
                    msg.getCreatedAt().toString()
            ));
        }

        int totalUserTokens = chatMessageRepository.getTotalUserTokensBySessionId(
            session.getId(), 
            ChatMessage.MessageRole.USER
        );
        int totalAssistantTokens = chatMessageRepository.getTotalUserTokensBySessionId(
            session.getId(), 
            ChatMessage.MessageRole.ASSISTANT
        );
        int totalTokens = totalUserTokens + totalAssistantTokens;
        
        int tokensRemaining = chatProperties.getMaxTokensPerChat();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 1);
        org.springframework.data.domain.Page<ChatMessage> lastAssistantMessagesPage = chatMessageRepository.findLastMessagesBySessionIdAndRole(
            session.getId(), 
            ChatMessage.MessageRole.ASSISTANT,
            pageable
        );
        if (!lastAssistantMessagesPage.isEmpty() && lastAssistantMessagesPage.getContent().get(0).getTokensRemaining() != null) {
            tokensRemaining = lastAssistantMessagesPage.getContent().get(0).getTokensRemaining();
        } else {
            tokensRemaining = Math.max(0, chatProperties.getMaxTokensPerChat() - totalTokens);
        }
        
        return new ChatSessionResponse(
                session.getId(),
                session.getType().name(),
                session.getIdea() != null ? session.getIdea().getId() : null,
                ideaSummary,
                totalUserTokens,
                totalAssistantTokens,
                totalTokens,
                tokensRemaining,
                session.getLastResetAt().toString(),
                messageResponses
        );
    }

    private User getCurrentAuthenticatedUser() {
        return userCacheService.getCurrentAuthenticatedUser();
    }
    
    private String removeModerationTags(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        
        String cleaned = content
            .replaceAll("(?i)\\[MODERACAO\\s*:\\s*SEGURA\\s*\\]", "")
            .replaceAll("(?i)\\[MODERACAO\\s*:\\s*PERIGOSO\\s*\\]", "")
            .replaceAll("(?i)\\[\\s*MODERACAO\\s*:\\s*SEGURA\\s*\\]\\s*", "")
            .replaceAll("(?i)\\[\\s*MODERACAO\\s*:\\s*PERIGOSO\\s*\\]\\s*", "")
            .trim();
        
        if (cleaned.matches("(?i)^\\s*\\[MODERACAO\\s*:\\s*(SEGURA|PERIGOSO)\\s*\\].*")) {
            int endIndex = cleaned.indexOf("]");
            if (endIndex != -1) {
                cleaned = cleaned.substring(endIndex + 1).trim();
            }
        }
        
        return cleaned;
    }
}
