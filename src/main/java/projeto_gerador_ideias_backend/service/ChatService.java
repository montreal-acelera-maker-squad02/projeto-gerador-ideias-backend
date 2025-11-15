package projeto_gerador_ideias_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.dto.MessagePreparationResult;
import projeto_gerador_ideias_backend.dto.request.ChatMessageRequest;
import projeto_gerador_ideias_backend.dto.response.InteractionMetrics;
import projeto_gerador_ideias_backend.dto.request.StartChatRequest;
import projeto_gerador_ideias_backend.dto.response.ChatLogsResponse;
import projeto_gerador_ideias_backend.dto.response.ChatMessageResponse;
import projeto_gerador_ideias_backend.dto.response.ChatSessionResponse;
import projeto_gerador_ideias_backend.dto.response.IdeaSummaryResponse;
import projeto_gerador_ideias_backend.dto.response.Interaction;
import projeto_gerador_ideias_backend.dto.response.AdminInteraction;
import projeto_gerador_ideias_backend.dto.response.AdminChatLogsResponse;
import projeto_gerador_ideias_backend.dto.response.LogsSummary;
import projeto_gerador_ideias_backend.dto.response.PaginationInfo;
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

@Slf4j
@Service
public class ChatService {

    private static final String ERROR_SESSION_NOT_FOUND = "Sessão não encontrada: ";
    private static final String LOG_KEY_SESSION_ID = "sessionId";
    private static final String LOG_KEY_IDEA_ID = "ideaId";
    private static final String LOG_KEY_PREVIOUS_TOKENS_REMAINING = "previousTokensRemaining";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IdeaRepository ideaRepository;
    private final ChatProperties chatProperties;
    private final TokenCalculationService tokenCalculationService;
    private final PromptBuilderService promptBuilderService;
    private final OllamaIntegrationService ollamaIntegrationService;
    private final ChatLimitValidator chatLimitValidator;
    private final ContentModerationService contentModerationService;
    private final UserCacheService userCacheService;
    private final ChatMetricsService chatMetricsService;
    private final IdeaSummaryService ideaSummaryService;
    private final IpEncryptionService ipEncryptionService;
    @Lazy
    private ChatService self;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            IdeaRepository ideaRepository,
            ChatProperties chatProperties,
            TokenCalculationService tokenCalculationService,
            PromptBuilderService promptBuilderService,
            OllamaIntegrationService ollamaIntegrationService,
            ChatLimitValidator chatLimitValidator,
            ContentModerationService contentModerationService,
            UserCacheService userCacheService,
            ChatMetricsService chatMetricsService,
            IdeaSummaryService ideaSummaryService,
            IpEncryptionService ipEncryptionService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.ideaRepository = ideaRepository;
        this.chatProperties = chatProperties;
        this.tokenCalculationService = tokenCalculationService;
        this.promptBuilderService = promptBuilderService;
        this.ollamaIntegrationService = ollamaIntegrationService;
        this.chatLimitValidator = chatLimitValidator;
        this.contentModerationService = contentModerationService;
        this.userCacheService = userCacheService;
        this.chatMetricsService = chatMetricsService;
        this.ideaSummaryService = ideaSummaryService;
        this.ipEncryptionService = ipEncryptionService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@Lazy ChatService self) {
        this.self = self;
    }

    private ChatService getTransactionalService() {
        if (self != null && self != this) {
            try {
                if (self.chatSessionRepository != null) {
                    return self;
                }
            } catch (Exception e) {
                // Se o proxy não estiver totalmente inicializado, usa 'this' como fallback
                // Isso pode acontecer durante a inicialização do contexto do Spring
            }
        }
        return this;
    }

    @Transactional
    public ChatSessionResponse startChat(StartChatRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        Long ideaId = request.getIdeaId();
        Idea idea = ideaId != null ? validateAndGetIdea(ideaId, currentUser) : null;
        ChatSession session = findOrCreateSession(currentUser, ideaId, idea);
        
        log.info("Chat session started", Map.of(
            LOG_KEY_SESSION_ID, session.getId(),
            "chatType", session.getType().name(),
            LOG_KEY_IDEA_ID, ideaId != null ? ideaId : "null"
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
            (session.getCachedIdeaContent() == null || session.getCachedIdeaContext() == null) &&
            session.getIdea() != null) {
            session.setCachedIdeaContent(session.getIdea().getGeneratedContent());
            session.setCachedIdeaContext(session.getIdea().getContext());
            log.debug("Populated idea context cache in memory", Map.of(
                LOG_KEY_SESSION_ID, session.getId(),
                LOG_KEY_IDEA_ID, session.getIdea().getId()
            ));
        }
    }
    
    private void ensureIdeaContextCacheAndSave(ChatSession session) {
        if (session.getType() == ChatSession.ChatType.IDEA_BASED && 
            (session.getCachedIdeaContent() == null || session.getCachedIdeaContext() == null) &&
            session.getIdea() != null) {
            session.setCachedIdeaContent(session.getIdea().getGeneratedContent());
            session.setCachedIdeaContext(session.getIdea().getContext());
            chatSessionRepository.save(session);
            log.debug("Populated and saved idea context cache", Map.of(
                LOG_KEY_SESSION_ID, session.getId(),
                LOG_KEY_IDEA_ID, session.getIdea().getId()
            ));
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
    public ChatMessageResponse sendMessage(Long sessionId, ChatMessageRequest request, String clientIp) {
        long startTime = System.currentTimeMillis();
        String chatType = "UNKNOWN";
        
        try {
            ChatService transactionalService = getTransactionalService();
            MessagePreparationResult preparation = transactionalService.prepareMessage(sessionId, request);
            chatType = preparation.isFreeChat() ? "FREE" : "IDEA_BASED";
            
            OllamaResponseResult ollamaResult = callOllamaAndValidate(sessionId, preparation);
            ChatMessageResponse response = transactionalService.saveMessageAndResponse(sessionId, preparation, ollamaResult.getResponse(), ollamaResult.getTokens(), clientIp);
            
            recordSuccessMetrics(startTime, chatType, preparation.getMessageTokens(), ollamaResult.getTokens());
            return response;
        } catch (ValidationException | ResourceNotFoundException | ChatPermissionException | TokenLimitExceededException | OllamaServiceException e) {
            handleKnownException(e);
            throw e;
        } catch (InvalidDataAccessApiUsageException e) {
            log.error("Transaction error in sendMessage", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
            throw new OllamaServiceException("Erro ao processar mensagem. Tente novamente.", e);
        } catch (DataAccessException e) {
            log.error("Database access error in sendMessage", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
            throw new OllamaServiceException("Erro ao acessar o banco de dados. Tente novamente.", e);
        } catch (Exception e) {
            handleUnexpectedException(e, sessionId, startTime, chatType);
            throw new OllamaServiceException("Erro inesperado ao processar mensagem: " + e.getMessage(), e);
        }
    }

    private OllamaResponseResult callOllamaAndValidate(Long sessionId, MessagePreparationResult preparation) {
        try {
            List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> historyMessages = preparation.getHistoryMessages();
            String aiResponse = historyMessages == null || historyMessages.isEmpty()
                ? ollamaIntegrationService.callOllamaWithSystemPrompt(preparation.getSystemPrompt(), preparation.getUserMessage())
                : ollamaIntegrationService.callOllamaWithHistory(preparation.getSystemPrompt(), historyMessages, preparation.getUserMessage());
            
            String normalizedResponse = contentModerationService.validateAndNormalizeResponse(aiResponse, preparation.isFreeChat());
            int responseTokens = tokenCalculationService.estimateTokens(normalizedResponse);
            
                if (responseTokens < 0) {
                log.warn("Negative token count calculated, setting to 0", Map.of(LOG_KEY_SESSION_ID, sessionId, "responseTokens", responseTokens));
                    responseTokens = 0;
                }
            
            return new OllamaResponseResult(normalizedResponse, responseTokens);
            } catch (ValidationException e) {
            log.error("Validation error during message processing", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
                chatMetricsService.recordValidationError("moderation");
                throw e;
            } catch (OllamaServiceException e) {
            log.error("Ollama service error", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
                chatMetricsService.recordOllamaError("service_error");
                throw e;
            } catch (OptimisticLockException e) {
            log.warn("Optimistic lock conflict during Ollama call", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
                throw new TokenLimitExceededException("Sessão foi atualizada por outra requisição. Tente novamente.");
            } catch (Exception e) {
            log.error("Unexpected error during Ollama call", Map.of(LOG_KEY_SESSION_ID, sessionId, "errorType", e.getClass().getName()), e);
                throw new OllamaServiceException("Erro ao comunicar com a IA: " + e.getMessage(), e);
        }
            }

    private void recordSuccessMetrics(long startTime, String chatType, int userTokens, int assistantTokens) {
            long duration = System.currentTimeMillis() - startTime;
            chatMetricsService.recordMessageSent(chatType);
            chatMetricsService.recordMessageProcessingTime(duration, chatType, true);
        chatMetricsService.recordTokenUsage(userTokens, "USER");
        chatMetricsService.recordTokenUsage(assistantTokens, "ASSISTANT");
    }
            
    private void handleKnownException(Exception e) {
        if (e instanceof ValidationException) {
            chatMetricsService.recordValidationError("message_limits");
        }
    }

    private void handleUnexpectedException(Exception e, Long sessionId, long startTime, String chatType) {
            long duration = System.currentTimeMillis() - startTime;
            chatMetricsService.recordMessageProcessingTime(duration, chatType, false);
        log.error("Unexpected error in sendMessage", Map.of(LOG_KEY_SESSION_ID, sessionId, "errorType", e.getClass().getName()), e);
    }

    private static class OllamaResponseResult {
        private final String response;
        private final int tokens;

        OllamaResponseResult(String response, int tokens) {
            this.response = response;
            this.tokens = tokens;
        }

        String getResponse() {
            return response;
        }

        int getTokens() {
            return tokens;
        }
    }

    @Transactional
    MessagePreparationResult prepareMessage(Long sessionId, ChatMessageRequest request) {
        ChatSession session = findSessionWithRetry(sessionId);
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
        
        List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> historyMessages = 
            promptBuilderService.buildMessageHistory(previousMessages);

        if (!historyMessages.isEmpty()) {
            log.debug("Message history for session", Map.of(
                LOG_KEY_SESSION_ID, sessionId,
                "historySize", historyMessages.size(),
                "historyRoles", historyMessages.stream()
                    .map(projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message::getRole)
                    .collect(java.util.stream.Collectors.joining(", "))
            ));
        }

        log.info("Processing message", Map.of(
            LOG_KEY_SESSION_ID, sessionId,
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

    private ChatSession findSessionWithRetry(Long sessionId) {
        try {
            return chatSessionRepository.findByIdWithLock(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception in prepareMessage, retrying without lock", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
            return chatSessionRepository.findByIdWithIdea(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        }
    }

    @Transactional
    ChatMessageResponse saveMessageAndResponse(
            Long sessionId, 
            MessagePreparationResult preparation, 
            String aiResponse, 
            int responseTokens,
            String clientIp) {
        
        ChatSession session;
        try {
            session = chatSessionRepository.findByIdWithLock(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception in saveMessageAndResponse, retrying without lock", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
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
            String encryptedIp = ipEncryptionService.encryptIp(clientIp);
            userMessageEntity.setIpAddress(encryptedIp);
            log.debug("IP capturado: {}, IP criptografado: {}", clientIp, encryptedIp != null ? "***" : "null");
            chatMessageRepository.save(userMessageEntity);
            chatMessageRepository.flush();

            String cleanedResponse = removeModerationTags(aiResponse);
            
            if (cleanedResponse == null || cleanedResponse.isBlank()) {
                log.warn("Response is empty after cleaning moderation tags", Map.of(LOG_KEY_SESSION_ID, sessionId));
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
                LOG_KEY_SESSION_ID, sessionId,
                "totalAssistantMessages", allAssistantMessages.size(),
                "foundMessageWithTokens", lastAssistantMessageWithTokens != null
            ));
            
            if (lastAssistantMessageWithTokens != null) {
                previousTokensRemaining = lastAssistantMessageWithTokens.getTokensRemaining();
                log.info("Using previous tokensRemaining", Map.of(
                    LOG_KEY_PREVIOUS_TOKENS_REMAINING, previousTokensRemaining,
                    "lastAssistantMessageId", lastAssistantMessageWithTokens.getId(),
                    "lastAssistantMessageCreatedAt", lastAssistantMessageWithTokens.getCreatedAt()
                ));
                    } else {
                log.info("No previous assistant messages with tokensRemaining found, using initial value", Map.of(
                    "initialTokensRemaining", previousTokensRemaining,
                    LOG_KEY_SESSION_ID, sessionId
                ));
            }
            
            int tokensRemaining = Math.max(0, previousTokensRemaining - totalTokensThisMessage);
            
            log.debug("Token calculation", Map.of(
                LOG_KEY_SESSION_ID, sessionId,
                LOG_KEY_PREVIOUS_TOKENS_REMAINING, previousTokensRemaining,
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
                LOG_KEY_SESSION_ID, sessionId,
                "tokensInputThisMessage", tokensInputThisMessage,
                "tokensOutputThisMessage", tokensOutputThisMessage,
                "totalTokensThisMessage", totalTokensThisMessage,
                "totalTokensAccumulated", totalTokensAccumulated,
                LOG_KEY_PREVIOUS_TOKENS_REMAINING, previousTokensRemaining,
                "tokensRemaining", tokensRemaining
            ));

            log.info("Message processed successfully", Map.of(
            LOG_KEY_SESSION_ID, sessionId,
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
            log.warn("Optimistic lock conflict", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
            throw new TokenLimitExceededException("Sessão foi atualizada por outra requisição. Tente novamente.");
        } catch (ResourceNotFoundException e) {
            log.warn("Session not found during save", Map.of(LOG_KEY_SESSION_ID, sessionId), e);
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
    
    private boolean hasMoreMessagesBefore(Long sessionId, LocalDateTime beforeTimestamp) {
        return chatMessageRepository.countMessagesBeforeTimestamp(sessionId, beforeTimestamp) > 0;
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
    public projeto_gerador_ideias_backend.dto.response.OlderMessagesResponse getOlderMessages(Long sessionId, String beforeTimestamp, Integer limit) {
        validateSessionAndPermission(sessionId);
        LocalDateTime before = parseTimestamp(beforeTimestamp);
        int messageLimit = calculateMessageLimit(limit);
        
        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(0, messageLimit + 1);
        
        List<ChatMessage> messages = chatMessageRepository.findMessagesBeforeTimestamp(
            sessionId, 
            before, 
            pageable
        );
        
        boolean hasMore = messages.size() > messageLimit;
        if (hasMore) {
            messages = messages.subList(0, messageLimit);
        }
        
        messages.sort((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));
        
        List<ChatMessageResponse> messageResponses = messages.stream()
            .map(msg -> mapMessageToResponse(sessionId, msg))
            .toList();
        
        return new projeto_gerador_ideias_backend.dto.response.OlderMessagesResponse(messageResponses, hasMore);
    }

    private void validateSessionAndPermission(Long sessionId) {
        ChatSession session = chatSessionRepository.findByIdWithIdea(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND + sessionId));
        
        User currentUser = getCurrentAuthenticatedUser();
        if (!java.util.Objects.equals(session.getUser().getId(), currentUser.getId())) {
            throw new ChatPermissionException("Você não tem permissão para esta sessão.");
        }
    }

    private LocalDateTime parseTimestamp(String beforeTimestamp) {
        try {
            return LocalDateTime.parse(beforeTimestamp);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ValidationException("Formato de timestamp inválido. Use ISO 8601 (ex: 2025-11-08T13:00:00)");
        }
    }

    private int calculateMessageLimit(Integer limit) {
        return (limit != null && limit > 0) ? Math.min(limit, 50) : 20;
    }

    private ChatMessageResponse mapMessageToResponse(Long sessionId, ChatMessage msg) {
        TokenAccumulationResult tokenResult = calculateAccumulatedTokens(sessionId, msg);
        int tokensRemainingForMessage = calculateTokensRemainingForMessage(msg, tokenResult.getAllMessagesUpToThis());
        
        return new ChatMessageResponse(
            msg.getId(),
            msg.getRole().name().toLowerCase(),
            msg.getContent(),
            tokenResult.getAccumulatedUserTokens(),
            tokenResult.getAccumulatedAssistantTokens(),
            tokenResult.getTotalTokens(),
            tokensRemainingForMessage,
            msg.getCreatedAt().toString()
        );
    }

    private TokenAccumulationResult calculateAccumulatedTokens(Long sessionId, ChatMessage msg) {
        List<ChatMessage> allMessagesUpToThis = getMessagesUpToTimestamp(sessionId, msg.getCreatedAt());
        
        int accumulatedUserTokens = 0;
        int accumulatedAssistantTokens = 0;
        
        for (ChatMessage m : allMessagesUpToThis) {
            if (m.getRole() == ChatMessage.MessageRole.USER) {
                accumulatedUserTokens += m.getTokensUsed();
            } else {
                accumulatedAssistantTokens += m.getTokensUsed();
            }
        }
        
        int totalTokens = accumulatedUserTokens + accumulatedAssistantTokens;
        return new TokenAccumulationResult(accumulatedUserTokens, accumulatedAssistantTokens, totalTokens, allMessagesUpToThis);
    }

    private List<ChatMessage> getMessagesUpToTimestamp(Long sessionId, LocalDateTime timestamp) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
            .stream()
            .filter(m -> m.getCreatedAt().isBefore(timestamp) || m.getCreatedAt().equals(timestamp))
            .toList();
    }

    private int calculateTokensRemainingForMessage(ChatMessage msg, List<ChatMessage> allMessagesUpToThis) {
        if (msg.getRole() == ChatMessage.MessageRole.ASSISTANT && msg.getTokensRemaining() != null) {
            return msg.getTokensRemaining();
        }
        
        if (msg.getRole() == ChatMessage.MessageRole.USER) {
            return calculateTokensRemainingForUserMessage(msg, allMessagesUpToThis);
        }
        
        return chatProperties.getMaxTokensPerChat();
    }

    private int calculateTokensRemainingForUserMessage(ChatMessage msg, List<ChatMessage> allMessagesUpToThis) {
        int msgIndex = allMessagesUpToThis.indexOf(msg);
        if (msgIndex < 0 || msgIndex + 1 >= allMessagesUpToThis.size()) {
            return chatProperties.getMaxTokensPerChat();
        }
        
        ChatMessage nextMsg = allMessagesUpToThis.get(msgIndex + 1);
        if (nextMsg.getRole() == ChatMessage.MessageRole.ASSISTANT && nextMsg.getTokensRemaining() != null) {
            int tokensUsedBetween = msg.getTokensUsed() + nextMsg.getTokensUsed();
            return nextMsg.getTokensRemaining() + tokensUsedBetween;
        }
        
        return chatProperties.getMaxTokensPerChat();
    }

    private static class TokenAccumulationResult {
        private final int accumulatedUserTokens;
        private final int accumulatedAssistantTokens;
        private final int totalTokens;
        private final List<ChatMessage> allMessagesUpToThis;

        TokenAccumulationResult(int accumulatedUserTokens, int accumulatedAssistantTokens, 
                               int totalTokens, List<ChatMessage> allMessagesUpToThis) {
            this.accumulatedUserTokens = accumulatedUserTokens;
            this.accumulatedAssistantTokens = accumulatedAssistantTokens;
            this.totalTokens = totalTokens;
            this.allMessagesUpToThis = allMessagesUpToThis;
        }

        int getAccumulatedUserTokens() {
            return accumulatedUserTokens;
        }

        int getAccumulatedAssistantTokens() {
            return accumulatedAssistantTokens;
        }

        int getTotalTokens() {
            return totalTokens;
        }

        List<ChatMessage> getAllMessagesUpToThis() {
            return allMessagesUpToThis;
        }
    }

    @Transactional
    public List<IdeaSummaryResponse> getUserIdeasSummary() {
        User currentUser = getCurrentAuthenticatedUser();
        List<Object[]> results = ideaRepository.findIdeasSummaryOnlyByUserId(currentUser.getId());
        
        List<IdeaSummaryResponse> responses = new ArrayList<>();
        List<Idea> ideasToUpdate = new ArrayList<>();
        
        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            String summary = row[1] != null ? (String) row[1] : "";
            String themeName = row[2] != null ? (String) row[2] : "";
            LocalDateTime createdAt = (LocalDateTime) row[3];
            
            if (summary == null || summary.isBlank()) {
                Idea idea = ideaRepository.findById(id).orElse(null);
                if (idea != null) {
                    summary = ideaSummaryService.summarizeIdeaSimple(idea.getGeneratedContent());
                    idea.setSummary(summary);
                    ideasToUpdate.add(idea);
                } else {
                    summary = "Sem resumo disponível";
                }
            }
            
            responses.add(new IdeaSummaryResponse(
                    id,
                    summary,
                    themeName,
                    createdAt.toString()
            ));
        }
        
        if (!ideasToUpdate.isEmpty()) {
            ideaRepository.saveAll(ideasToUpdate);
        }
        
        return responses;
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
        
        int i = 0;
        while (i < messages.size()) {
            ChatMessage message = messages.get(i);
            
            if (!isValidUserMessage(message)) {
                i++;
                continue;
            }
            
            ChatSession session = message.getSession();
            ChatMessage assistantMessage = findMatchingAssistantMessage(messages, i, session);
            
            Interaction interaction = createInteraction(
                interactionId++,
                message,
                session,
                assistantMessage
            );
            
            interactions.add(interaction);
            
            i++;
            if (assistantMessage != null) {
                i++;
            }
        }
        
        return interactions;
    }

    private boolean isValidUserMessage(ChatMessage message) {
        if (message == null) {
            return false;
        }
        
        if (message.getSession() == null) {
                log.warn("Message without session", Map.of("messageId", message.getId()));
            return false;
        }
        
        return message.getRole() == ChatMessage.MessageRole.USER;
    }

    private ChatMessage findMatchingAssistantMessage(List<ChatMessage> messages, int currentIndex, ChatSession session) {
        if (currentIndex + 1 >= messages.size()) {
            return null;
        }
        
        ChatMessage nextMessage = messages.get(currentIndex + 1);
        if (nextMessage == null) {
            return null;
        }
        
        if (nextMessage.getRole() != ChatMessage.MessageRole.ASSISTANT) {
            return null;
        }
        
        if (nextMessage.getSession() == null) {
            return null;
        }
        
        if (!nextMessage.getSession().getId().equals(session.getId())) {
            return null;
        }
        
        return nextMessage;
    }

    private Interaction createInteraction(long interactionId, ChatMessage userMessage, ChatSession session, ChatMessage assistantMessage) {
        String userContent = userMessage.getContent();
        int tokensInput = userMessage.getTokensUsed();
        String timestamp = userMessage.getCreatedAt() != null 
            ? userMessage.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        if (assistantMessage != null) {
            return createInteractionWithAssistant(interactionId, userContent, tokensInput, timestamp, session, userMessage, assistantMessage);
        } else {
            return createInteractionWithoutAssistant(interactionId, userContent, tokensInput, timestamp, session);
        }
    }

    private Interaction createInteractionWithAssistant(long interactionId, String userMessageContent, int tokensInput, 
                                                       String timestamp, ChatSession session, ChatMessage userMessage, ChatMessage assistantMessage) {
        String assistantContent = assistantMessage.getContent();
        int tokensOutput = assistantMessage.getTokensUsed();
        int totalTokens = tokensInput + tokensOutput;
        Long responseTimeMs = calculateResponseTime(userMessage, assistantMessage);
        
        InteractionMetrics metrics = new InteractionMetrics(
            tokensInput,
            tokensOutput,
                            totalTokens,
            responseTimeMs
        );
        
        return new Interaction(
            interactionId,
            timestamp,
                    session.getId(),
                    session.getType().name(),
            session.getIdea() != null ? session.getIdea().getId() : null,
            userMessageContent,
            assistantContent,
            metrics
        );
    }

    private Interaction createInteractionWithoutAssistant(long interactionId, String userMessage, int tokensInput, 
                                                          String timestamp, ChatSession session) {
        InteractionMetrics metrics = new InteractionMetrics(
            tokensInput,
            0,
            tokensInput,
            null
        );
        
        return new Interaction(
            interactionId,
            timestamp,
            session.getId(),
            session.getType().name(),
            session.getIdea() != null ? session.getIdea().getId() : null,
            userMessage,
            null,
            metrics
        );
    }

    private Long calculateResponseTime(ChatMessage userMessage, ChatMessage assistantMessage) {
        if (assistantMessage.getCreatedAt() == null || userMessage.getCreatedAt() == null) {
            return null;
        }
        
        return java.time.Duration.between(
            userMessage.getCreatedAt(),
            assistantMessage.getCreatedAt()
        ).toMillis();
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
    

    private ChatSessionResponse buildSessionResponse(ChatSession session, List<ChatMessage> messages) {
        String ideaSummary = getIdeaSummary(session);
        List<ChatMessageResponse> messageResponses = buildMessageResponses(messages);
        TokenSummary tokenSummary = calculateTokenSummary(session);
        int tokensRemaining = calculateTokensRemaining(session, tokenSummary.getTotalTokens());
        
        boolean hasMore = false;
        if (!messages.isEmpty()) {
            LocalDateTime oldestMessageTime = messages.get(0).getCreatedAt();
            hasMore = hasMoreMessagesBefore(session.getId(), oldestMessageTime);
        }
        
        return new ChatSessionResponse(
                session.getId(),
                session.getType().name(),
                session.getIdea() != null ? session.getIdea().getId() : null,
                ideaSummary,
                tokenSummary.getTotalUserTokens(),
                tokenSummary.getTotalAssistantTokens(),
                tokenSummary.getTotalTokens(),
                tokensRemaining,
                session.getLastResetAt().toString(),
                messageResponses,
                hasMore
        );
    }

    private String getIdeaSummary(ChatSession session) {
        if (session.getIdea() == null) {
            return null;
        }
        String summary = session.getIdea().getSummary();
        if (summary == null || summary.isBlank()) {
            summary = ideaSummaryService.summarizeIdeaSimple(session.getIdea().getGeneratedContent());
        }
        return summary;
    }

    private List<ChatMessageResponse> buildMessageResponses(List<ChatMessage> messages) {
        List<ChatMessageResponse> messageResponses = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            MessageTokenInfo tokenInfo = calculateMessageTokenInfo(messages, i, msg);
            
            messageResponses.add(new ChatMessageResponse(
                    msg.getId(),
                    msg.getRole().name().toLowerCase(),
                    msg.getContent(),
                    tokenInfo.getTokensInput(),
                    tokenInfo.getTokensOutput(),
                    tokenInfo.getTotalTokens(),
                    tokenInfo.getTokensRemaining(),
                    msg.getCreatedAt().toString()
            ));
        }

        return messageResponses;
    }

    private MessageTokenInfo calculateMessageTokenInfo(List<ChatMessage> messages, int index, ChatMessage msg) {
        if (msg.getRole() == ChatMessage.MessageRole.USER) {
            return calculateUserMessageTokenInfo(messages, index, msg);
        } else {
            return calculateAssistantMessageTokenInfo(messages, index, msg);
        }
    }

    private MessageTokenInfo calculateUserMessageTokenInfo(List<ChatMessage> messages, int index, ChatMessage msg) {
        int tokensInput = msg.getTokensUsed();
        int tokensOutput = 0;
        int tokensRemaining = chatProperties.getMaxTokensPerChat();
        
        if (index + 1 < messages.size() && 
            messages.get(index + 1).getRole() == ChatMessage.MessageRole.ASSISTANT) {
            ChatMessage nextMsg = messages.get(index + 1);
            tokensOutput = nextMsg.getTokensUsed();
            if (nextMsg.getTokensRemaining() != null) {
                tokensRemaining = nextMsg.getTokensRemaining() + tokensInput + tokensOutput;
            } else {
                tokensRemaining = chatProperties.getMaxTokensPerChat();
            }
        }
        
        return new MessageTokenInfo(tokensInput, tokensOutput, tokensRemaining);
    }

    private MessageTokenInfo calculateAssistantMessageTokenInfo(List<ChatMessage> messages, int index, ChatMessage msg) {
        int tokensInput = 0;
        int tokensOutput = msg.getTokensUsed();
        int tokensRemaining = chatProperties.getMaxTokensPerChat();
        
        if (index > 0) {
            ChatMessage prevMsg = messages.get(index - 1);
            if (prevMsg.getRole() == ChatMessage.MessageRole.USER) {
                tokensInput = prevMsg.getTokensUsed();
            }
        }
        
        if (msg.getTokensRemaining() != null) {
            tokensRemaining = msg.getTokensRemaining();
        }
        
        return new MessageTokenInfo(tokensInput, tokensOutput, tokensRemaining);
    }

    private TokenSummary calculateTokenSummary(ChatSession session) {
        int totalUserTokens = chatMessageRepository.getTotalUserTokensBySessionId(
            session.getId(), 
            ChatMessage.MessageRole.USER
        );
        int totalAssistantTokens = chatMessageRepository.getTotalUserTokensBySessionId(
            session.getId(), 
            ChatMessage.MessageRole.ASSISTANT
        );
        int totalTokens = totalUserTokens + totalAssistantTokens;
        
        return new TokenSummary(totalUserTokens, totalAssistantTokens, totalTokens);
    }

    private int calculateTokensRemaining(ChatSession session, int totalTokens) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 1);
        org.springframework.data.domain.Page<ChatMessage> lastAssistantMessagesPage = chatMessageRepository.findLastMessagesBySessionIdAndRole(
                session.getId(),
            ChatMessage.MessageRole.ASSISTANT,
            pageable
        );
        
        if (!lastAssistantMessagesPage.isEmpty()) {
            ChatMessage lastAssistantMsg = lastAssistantMessagesPage.getContent().get(0);
            if (lastAssistantMsg.getTokensRemaining() != null) {
                return lastAssistantMsg.getTokensRemaining();
            }
        }
        
        return Math.max(0, chatProperties.getMaxTokensPerChat() - totalTokens);
    }

    private static class MessageTokenInfo {
        private final int tokensInput;
        private final int tokensOutput;
        private final int tokensRemaining;

        MessageTokenInfo(int tokensInput, int tokensOutput, int tokensRemaining) {
            this.tokensInput = tokensInput;
            this.tokensOutput = tokensOutput;
            this.tokensRemaining = tokensRemaining;
        }

        int getTokensInput() {
            return tokensInput;
        }

        int getTokensOutput() {
            return tokensOutput;
        }

        int getTotalTokens() {
            return tokensInput + tokensOutput;
        }

        int getTokensRemaining() {
            return tokensRemaining;
        }
    }

    private static class ChatMessages {
        final ChatMessage userMessage;
        final ChatMessage assistantMessage;

        ChatMessages(ChatMessage userMessage, ChatMessage assistantMessage) {
            this.userMessage = userMessage;
            this.assistantMessage = assistantMessage;
        }
    }

    private static class TokenSummary {
        private final int totalUserTokens;
        private final int totalAssistantTokens;
        private final int totalTokens;

        TokenSummary(int totalUserTokens, int totalAssistantTokens, int totalTokens) {
            this.totalUserTokens = totalUserTokens;
            this.totalAssistantTokens = totalAssistantTokens;
            this.totalTokens = totalTokens;
        }

        int getTotalUserTokens() {
            return totalUserTokens;
        }

        int getTotalAssistantTokens() {
            return totalAssistantTokens;
        }

        int getTotalTokens() {
            return totalTokens;
        }
    }

    public User getCurrentAuthenticatedUser() {
        return userCacheService.getCurrentAuthenticatedUser();
    }
    
    /**
     * Verifica se o usuário atual é admin (por role)
     */
    private boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole() == projeto_gerador_ideias_backend.model.Role.ADMIN;
    }
    
    /**
     * Obtém logs de chat para admin (todos os usuários ou de um usuário específico)
     */
    @Transactional(readOnly = true)
    public AdminChatLogsResponse getAdminChatLogs(String dateStr, Long userId, Integer page, Integer size) {
        User currentUser = getCurrentAuthenticatedUser();
        
        if (!isAdmin(currentUser)) {
            throw new ChatPermissionException("Acesso negado. Apenas administradores podem acessar este endpoint.");
        }
        
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
        
        List<ChatMessage> allMessages = chatMessageRepository.findByUserIdAndDateRangeAdmin(
            userId, startDate, endDate);
        
        List<AdminInteraction> allInteractions = transformMessagesToAdminInteractions(allMessages);
        
        int totalInteractions = allInteractions.size();
        int totalPages = (int) Math.ceil((double) totalInteractions / pageSize);
        
        int startIndex = pageNumber * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalInteractions);
        
        List<AdminInteraction> paginatedInteractions = new ArrayList<>();
        if (startIndex < totalInteractions) {
            paginatedInteractions = allInteractions.subList(startIndex, endIndex);
        }
        
        List<Interaction> interactionsForSummary = allInteractions.stream()
            .map(ai -> new Interaction(
                ai.getInteractionId(),
                ai.getTimestamp(),
                ai.getSessionId(),
                ai.getChatType(),
                ai.getIdeaId(),
                ai.getUserMessage(),
                ai.getAssistantMessage(),
                ai.getMetrics()
            ))
            .toList();
        
        LogsSummary summary = calculateSummary(interactionsForSummary);
        
        PaginationInfo pagination = new PaginationInfo(
                (long) totalInteractions,
                totalPages,
                pageNumber + 1,
                pageNumber + 1 < totalPages,
                pageNumber > 0
        );
        
        return new AdminChatLogsResponse(
                targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                userId,
                summary,
                paginatedInteractions,
                pagination
        );
    }
    
    private List<AdminInteraction> transformMessagesToAdminInteractions(List<ChatMessage> messages) {
        List<AdminInteraction> interactions = new ArrayList<>();
        long interactionId = 1;
        
        int i = 0;
        while (i < messages.size()) {
            ChatMessage message = messages.get(i);
            
            if (!isValidUserMessage(message)) {
                i++;
                continue;
            }
            
            ChatSession session = message.getSession();
            User user = session.getUser();
            ChatMessage assistantMessage = findMatchingAssistantMessage(messages, i, session);
            
            AdminInteraction interaction = createAdminInteraction(
                interactionId++,
                message,
                session,
                user,
                assistantMessage
            );
            
            interactions.add(interaction);
            
            i++;
            if (assistantMessage != null) {
                i++;
            }
        }
        
        return interactions;
    }
    
    private AdminInteraction createAdminInteraction(long interactionId, ChatMessage userMessage, 
                                                   ChatSession session, User user, ChatMessage assistantMessage) {
        String userContent = userMessage.getContent();
        int tokensInput = userMessage.getTokensUsed();
        String timestamp = userMessage.getCreatedAt() != null 
            ? userMessage.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        if (assistantMessage != null) {
            ChatMessages messages = new ChatMessages(userMessage, assistantMessage);
            return createAdminInteractionWithAssistant(interactionId, userContent, tokensInput, timestamp, 
                                                     session, user, messages);
        } else {
            return createAdminInteractionWithoutAssistant(interactionId, userMessage, tokensInput, timestamp, 
                                                        session, user);
        }
    }
    
    private AdminInteraction createAdminInteractionWithAssistant(long interactionId, String userMessageContent, 
                                                                 int tokensInput, String timestamp, ChatSession session,
                                                                 User user, ChatMessages messages) {
        String assistantContent = messages.assistantMessage.getContent();
        int tokensOutput = messages.assistantMessage.getTokensUsed();
        int totalTokens = tokensInput + tokensOutput;
        Long responseTimeMs = calculateResponseTime(messages.userMessage, messages.assistantMessage);
        
        InteractionMetrics metrics = new InteractionMetrics(
            tokensInput,
            tokensOutput,
            totalTokens,
            responseTimeMs
        );
        
        String userIp = messages.userMessage.getIpAddress() != null 
            ? ipEncryptionService.decryptIp(messages.userMessage.getIpAddress())
            : "unknown";
        
        return new AdminInteraction(
            interactionId,
            timestamp,
            session.getId(),
            session.getType().name(),
            session.getIdea() != null ? session.getIdea().getId() : null,
            user.getId(),
            user.getName(),
            user.getEmail(),
            userIp,
            userMessageContent,
            assistantContent,
            metrics
        );
    }
    
    private AdminInteraction createAdminInteractionWithoutAssistant(long interactionId, ChatMessage userMessageEntity, 
                                                                   int tokensInput, String timestamp, 
                                                                   ChatSession session, User user) {
        InteractionMetrics metrics = new InteractionMetrics(
            tokensInput,
            0,
            tokensInput,
            null
        );
        
        String userIp = userMessageEntity.getIpAddress() != null 
            ? ipEncryptionService.decryptIp(userMessageEntity.getIpAddress())
            : "unknown";
        
        return new AdminInteraction(
            interactionId,
            timestamp,
            session.getId(),
            session.getType().name(),
            session.getIdea() != null ? session.getIdea().getId() : null,
            user.getId(),
            user.getName(),
            user.getEmail(),
            userIp,
            userMessageEntity.getContent(),
            null,
            metrics
        );
    }
    
    private String removeModerationTags(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        
        String cleaned = content
            .replaceAll("(?i)\\[MODERACAO[\\s]*:[\\s]*SEGURA[\\s]*\\]", "")
            .replaceAll("(?i)\\[MODERACAO[\\s]*:[\\s]*PERIGOSO[\\s]*\\]", "")
            .trim();
        
        if (!cleaned.isEmpty() && cleaned.charAt(0) == '[') {
            int endIndex = cleaned.indexOf("]");
            if (endIndex != -1) {
                String prefix = cleaned.substring(0, Math.min(endIndex + 1, cleaned.length())).toUpperCase();
                if (prefix.contains("[MODERACAO") && (prefix.contains("SEGURA") || prefix.contains("PERIGOSO"))) {
                cleaned = cleaned.substring(endIndex + 1).trim();
                }
            }
        }
        
        return cleaned;
    }
}
