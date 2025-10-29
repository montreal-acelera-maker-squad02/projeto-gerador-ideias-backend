package projeto_gerador_ideias_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.dto.OllamaRequest;
import projeto_gerador_ideias_backend.dto.OllamaResponse;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.IdeaRepository;

@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final WebClient webClient;

    @Value("${ollama.model}")
    private String ollamaModel;

    private static final String SYSTEM_PROMPT = """
        Você é um assistente gerador de ideias. Sua resposta deve ser APENAS o texto da ideia e em português.
        Não adicione saudações, introduções, explicações ou conclusões.

        Regra de Segurança: Se o pedido for ilegal, ofensivo ou perigoso, sua ÚNICA resposta deve ser:
        "Desculpe, não posso gerar ideias sobre esse tema."
        """;

    public IdeaService(IdeaRepository ideaRepository,
                       WebClient.Builder webClientBuilder,
                       @Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.ideaRepository = ideaRepository;
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
    }

    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request) {
        long startTime = System.currentTimeMillis();

        String userPrompt = String.format("Gere uma ideia para um(a) %s com o tema %s.",
                request.getContext(),
                request.getTheme().getValue());

        OllamaRequest ollamaRequest = new OllamaRequest(ollamaModel, SYSTEM_PROMPT, userPrompt);

        try {
            OllamaResponse ollamaResponse = this.webClient.post()
                    .uri("/api/chat")
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .block();

            long executionTime = System.currentTimeMillis() - startTime;

            if (ollamaResponse != null && ollamaResponse.getMessage() != null) {

                String generatedContent = ollamaResponse.getMessage().getContent();

                Idea newIdea = new Idea(
                        request.getTheme(),
                        request.getContext(),
                        generatedContent,
                        ollamaModel,
                        executionTime
                );
                Idea savedIdea = ideaRepository.save(newIdea);
                return new IdeaResponse(savedIdea);
            } else {
                throw new RuntimeException("Resposta nula ou inválida do Ollama (/api/chat).");
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao se comunicar com a IA (Ollama): " + e.getMessage(), e);
        }
    }
}