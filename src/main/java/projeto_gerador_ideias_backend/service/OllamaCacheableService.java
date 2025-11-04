package projeto_gerador_ideias_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.OllamaRequest;
import projeto_gerador_ideias_backend.dto.OllamaResponse;

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
     * A anotação @Cacheable armazena o 'String' retornado em um cache chamado 'aiResponseCache'.
     * A chave é o próprio prompt.
     */
    @Cacheable(value = "aiResponseCache", key = "#prompt")
    public String getAiResponse(String prompt) {
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
                throw new RuntimeException("Resposta nula ou inválida do Ollama (/api/chat).");
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao se comunicar com a IA (Ollama): " + e.getMessage(), e);
        }
    }
}