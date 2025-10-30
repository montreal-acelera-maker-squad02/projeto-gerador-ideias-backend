package projeto_gerador_ideias_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final WebClient webClient;

    @Value("${ollama.model}")
    private String ollamaModel;

    public IdeaService(IdeaRepository ideaRepository,
                       WebClient.Builder webClientBuilder,
                       @Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.ideaRepository = ideaRepository;
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
    }

    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request) {
        long startTime = System.currentTimeMillis();

        String promptMestre = construirPromptMestre(request.getTheme(), request.getContext());
        OllamaRequest ollamaRequest = new OllamaRequest(ollamaModel, promptMestre);

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

                return new IdeaResponse(newIdea);
            } else {
                throw new RuntimeException("Resposta nula ou inválida do Ollama (/api/chat).");
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao se comunicar com a IA (Ollama): " + e.getMessage(), e);
        }
    }

    private String construirPromptMestre(Theme category, String context) {

        String pedidoDoUsuario = String.format("Gere uma ideia para um(a) %s com o tema %s.", context, category.getValue());

        String regras = """
        Você é um assistente de IA focado em gerar ideias.

        ### REGRAS DE FORMATAÇÃO E TOM (MUITO IMPORTANTE) ###
        1. RESPONDA APENAS A IDEIA.
        2. NÃO adicione introduções, saudações ou comentários.
        3. NÃO adicione conclusões ou perguntas.
        4. Seja direto, profissional e objetivo.
        5. RESPONDA APENAS EM PORTUGUÊS DO BRASIL.
        
        ### REGRAS DE SEGURANÇA ###
        1. Se o usuário pedir algo usando palavras de baixo calão (profanidades) ou pedir ideias ilegais/negativas, 
           RECUSE educadamente. Responda APENAS: "Desculpe, não posso gerar ideias sobre esse tema."

        ### PEDIDO DO USUÁRIO ###
        """;

        return regras + pedidoDoUsuario;
    }


    public List<IdeaResponse> listarHistoricoIdeiasFiltrado(String theme, LocalDateTime startDate, LocalDateTime endDate) {
        List<Idea> ideias;

        if (theme != null && startDate != null && endDate != null) {
            ideias = ideaRepository.findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(Theme.valueOf(theme.toUpperCase()), startDate, endDate);
        } else if (theme != null) {
            ideias = ideaRepository.findByThemeOrderByCreatedAtDesc(Theme.valueOf(theme.toUpperCase()));
        } else if (startDate != null && endDate != null) {
            ideias = ideaRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
        } else {
            ideias = ideaRepository.findAllByOrderByCreatedAtDesc();
        }

        return ideias.stream().map(IdeaResponse::new).toList();
    }

}