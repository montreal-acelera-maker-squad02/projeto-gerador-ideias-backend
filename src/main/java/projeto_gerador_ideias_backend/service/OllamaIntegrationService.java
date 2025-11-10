package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.config.ChatProperties;
import projeto_gerador_ideias_backend.dto.request.OllamaRequest;
import projeto_gerador_ideias_backend.dto.response.OllamaResponse;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaIntegrationService {

    private final WebClient webClient;
    private final ChatProperties chatProperties;
    private final projeto_gerador_ideias_backend.service.ChatMetricsService chatMetricsService;

    @Value("${ollama.model}")
    private String ollamaModel;
    
    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;
    
    private static final Pattern MODERATION_DANGEROUS_PATTERN = 
        Pattern.compile("^\\s*\\[MODERACAO:\\s*PERIGOSO\\]\\s*$", Pattern.CASE_INSENSITIVE);
    
    private static final String LOG_KEY_MODEL = "model";
    private static final String LOG_KEY_DURATION_MS = "durationMs";
    private static final String LOG_KEY_STATUS_CODE = "statusCode";

    @Retryable(
        value = {OllamaServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String callOllamaWithSystemPrompt(String systemPrompt, String userPrompt) {
        OllamaRequest ollamaRequest = new OllamaRequest(ollamaModel, systemPrompt, userPrompt);
        return executeOllamaCall(ollamaRequest);
    }

    @Retryable(
        value = {OllamaServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String callOllamaWithHistory(String systemPrompt, List<OllamaRequest.Message> historyMessages, String userPrompt) {
        OllamaRequest ollamaRequest = new OllamaRequest(ollamaModel, systemPrompt, historyMessages, userPrompt);
        return executeOllamaCall(ollamaRequest);
    }

    private String executeOllamaCall(OllamaRequest ollamaRequest) {
        log.info("Sending request to Ollama", Map.of(
            LOG_KEY_MODEL, ollamaModel,
            "messagesCount", ollamaRequest.getMessages().size(),
            "systemPromptLength", ollamaRequest.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .findFirst()
                .map(m -> m.getContent().length())
                .orElse(0),
            "userPromptLength", ollamaRequest.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(m -> m.getContent().length())
                .orElse(0)
        ));
        
        long startTime = System.currentTimeMillis();
        
        try {
            OllamaResponse ollamaResponse = executeOllamaRequest(ollamaRequest);
            String content = extractResponseContent(ollamaResponse);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Ollama request completed", Map.of(
                LOG_KEY_MODEL, ollamaModel,
                LOG_KEY_DURATION_MS, duration,
                "responseLength", content.length()
            ));
            
            chatMetricsService.recordOllamaCallTime(duration);
            
            return content;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Ollama HTTP error", Map.of(
                LOG_KEY_MODEL, ollamaModel,
                LOG_KEY_STATUS_CODE, e.getStatusCode().value(),
                LOG_KEY_DURATION_MS, duration
            ), e);
            chatMetricsService.recordOllamaError("http_" + e.getStatusCode().value());
            throw handleWebClientException(e);
        } catch (OllamaServiceException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Ollama request failed", Map.of(
                LOG_KEY_MODEL, ollamaModel,
                LOG_KEY_DURATION_MS, duration
            ), e);
            chatMetricsService.recordOllamaError("generic");
            throw handleGenericException(e);
        }
    }

    private OllamaResponse executeOllamaRequest(OllamaRequest ollamaRequest) {
        try {
            log.debug("Executing Ollama request", Map.of(
                "baseUrl", webClient.toString(),
                LOG_KEY_MODEL, ollamaModel
            ));
            
            OllamaResponse response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                        resp -> resp.bodyToMono(String.class)
                            .map(body -> {
                                int statusCode = resp.statusCode().value();
                                String errorMsg;
                                
                                if (statusCode == 500) {
                                    errorMsg = String.format(
                                        "O servidor Ollama retornou erro 500. Verifique se o Ollama está rodando e se o modelo '%s' está disponível. " +
                                        "Para iniciar o Ollama, execute: ollama serve. Para verificar modelos disponíveis: ollama list",
                                        ollamaModel);
                                } else {
                                    errorMsg = String.format("Erro HTTP %d do Ollama: %s", statusCode, body);
                                }
                                
                                log.error("Ollama HTTP error", Map.of(
                                    LOG_KEY_STATUS_CODE, statusCode,
                                    "body", body,
                                    LOG_KEY_MODEL, ollamaModel
                                ));
                                return new OllamaServiceException(errorMsg);
                            }))
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofSeconds(chatProperties.getOllamaTimeoutSeconds())) 
                    .block();
            
            if (response == null) {
                throw new OllamaServiceException("Resposta nula do Ollama");
            }
            
            return response;
        } catch (org.springframework.web.reactive.function.client.WebClientRequestException e) {
            log.error("WebClient request error", Map.of(
                LOG_KEY_MODEL, ollamaModel,
                "message", e.getMessage()
            ), e);
            throw handleGenericException(e);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("WebClient response error", Map.of(
                LOG_KEY_MODEL, ollamaModel,
                LOG_KEY_STATUS_CODE, e.getStatusCode().value()
            ), e);
            throw handleWebClientException(e);
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.error("WebClient error during Ollama request", Map.of(LOG_KEY_MODEL, ollamaModel), e);
            throw handleGenericException(e);
        } catch (Exception e) {
            log.error("Unexpected error during Ollama request", Map.of(
                LOG_KEY_MODEL, ollamaModel,
                "errorType", e.getClass().getName()
            ), e);
            throw handleGenericException(e);
        }
    }

    private String extractResponseContent(OllamaResponse ollamaResponse) {
        if (ollamaResponse != null && ollamaResponse.getMessage() != null) {
            String content = ollamaResponse.getMessage().getContent();
            if (content == null) {
                throw new OllamaServiceException("Conteúdo da resposta do Ollama é nulo.");
            }
            
            String originalContent = content.trim();
            boolean isDangerous = originalContent.equalsIgnoreCase("[MODERACAO: PERIGOSO]") ||
                                 MODERATION_DANGEROUS_PATTERN.matcher(originalContent).matches();
            
            if (isDangerous) {
                return originalContent;
            }
            
            content = content.replaceAll("(?i)\\[MODERACAO\\s*:\\s*SEGURA\\s*\\]", "").trim();
            content = content.replaceAll("(?i)\\[MODERACAO\\s*:\\s*PERIGOSO\\s*\\]", "").trim();
            content = content.replaceAll("(?i)\\[\\s*MODERACAO\\s*:\\s*SEGURA\\s*\\]\\s*", "").trim();
            content = content.replaceAll("(?i)\\[\\s*MODERACAO\\s*:\\s*PERIGOSO\\s*\\]\\s*", "").trim();
            
            if (content.matches("(?i)^\\s*\\[MODERACAO\\s*:\\s*(SEGURA|PERIGOSO)\\s*\\].*")) {
                int endIndex = content.indexOf("]");
                if (endIndex != -1) {
                    content = content.substring(endIndex + 1).trim();
                }
            }
            
            content = content.trim();
            
            int maxLength = chatProperties.getMaxResponseLength();
            if (content.length() > maxLength) {
                log.warn("Ollama response exceeds maximum length", Map.of(
                    "responseLength", content.length(),
                    "maxLength", maxLength
                ));
                throw new OllamaServiceException(
                    String.format("Resposta do Ollama excede o tamanho máximo permitido (%d caracteres).", maxLength)
                );
            }
            
            return content.trim();
        }
        throw new OllamaServiceException("Resposta nula ou inválida do Ollama (/api/chat).");
    }

    private OllamaServiceException handleWebClientException(
            org.springframework.web.reactive.function.client.WebClientResponseException e) {
        String responseBody = e.getResponseBodyAsString();
        String errorDetail = !responseBody.isBlank() 
                ? responseBody 
                : e.getMessage();
        String errorMessage = String.format(
            "Erro ao se comunicar com a IA (Ollama): %s %s. Verifique se o Ollama está rodando e se o modelo '%s' está disponível.",
            e.getStatusCode(), 
            errorDetail,
            ollamaModel
        );
        return new OllamaServiceException(errorMessage, e);
    }

    private OllamaServiceException handleGenericException(Exception e) {
        if (isTimeoutException(e)) {
            return new OllamaServiceException(
                String.format("Timeout ao se comunicar com a IA (Ollama) após %d segundos. " +
                    "Verifique se o Ollama está rodando em %s. Para iniciar: ollama serve", 
                    chatProperties.getOllamaTimeoutSeconds(), ollamaBaseUrl), e);
        }
        if (isConnectionException(e)) {
            return new OllamaServiceException(
                String.format("Não foi possível conectar ao Ollama em %s. " +
                    "Verifique se o servidor está rodando. Para iniciar: ollama serve", 
                    ollamaBaseUrl), e);
        }
        return new OllamaServiceException(
            String.format("Erro ao se comunicar com a IA (Ollama): %s. Modelo: %s. " +
                "Verifique se o Ollama está rodando em %s.", 
                e.getMessage(), ollamaModel, ollamaBaseUrl), e);
    }

    private boolean isTimeoutException(Exception e) {
        Throwable cause = e.getCause();
        return cause instanceof java.util.concurrent.TimeoutException || 
               (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"));
    }

    private boolean isConnectionException(Exception e) {
        if (e.getMessage() == null) {
            return false;
        }
        String message = e.getMessage();
        return message.contains("Connection refused") || 
               message.contains("Connection reset") ||
               message.contains("connect");
    }

    @Recover
    public String recoverOllamaCall(OllamaServiceException e, String systemPrompt, String userPrompt) {
        log.error("All retry attempts failed for Ollama", Map.of(
            LOG_KEY_MODEL, ollamaModel,
            "systemPromptLength", systemPrompt.length(),
            "userPromptLength", userPrompt.length()
        ), e);
        throw new OllamaServiceException("Serviço de IA temporariamente indisponível. Tente novamente em alguns instantes.", e);
    }
}


