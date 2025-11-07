package projeto_gerador_ideias_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.OllamaRequest;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;

@Service
public class OllamaCacheableService {

    private final WebClient webClient;
    private final String ollamaModel;

    public OllamaCacheableService(WebClient.Builder webClientBuilder,
                                  @Value("${ollama.base-url}") String ollamaBaseUrl,
                                  @Value("${ollama.model}") String ollamaModel) {
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
        this.ollamaModel = ollamaModel;
    }

    /**
     * Método que realmente chama a IA.
     * A anotação @Cacheable armazena o 'String' retornado em um cache chamado 'aiResponseCache'.
     * A chave é o próprio prompt.
     */
    @Cacheable(value = "aiResponseCache", key = "#prompt")
    public String getAiResponse(String prompt) {
        return internalCallOllama(prompt);
    }

    /**
     * Método chama a IA diretamente, IGNORANDO o cache @Cacheable.
     */
    public String getAiResponseBypassingCache(String prompt) {
        return internalCallOllama(prompt);
    }

    /**
     * Método privado que contém a lógica real da chamada ao Ollama.
     */
    private String internalCallOllama(String prompt) {
        OllamaRequest ollamaRequest = new OllamaRequest(ollamaModel, prompt);
        try {
            OllamaResponse ollamaResponse = this.webClient.post()
                    .uri("/api/chat")
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .block();

            if (ollamaResponse != null && ollamaResponse.getMessage() != null) {
                 return ollamaResponse.getMessage().getContent().trim();
            } else {
                throw new OllamaServiceException("Resposta nula ou inválida do Ollama (/api/chat).");
            }
        } catch (OllamaServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaServiceException("Erro ao se comunicar com a IA (Ollama): " + e.getMessage(), e);
        }
    }
}