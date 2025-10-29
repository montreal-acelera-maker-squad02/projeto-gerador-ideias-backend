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
import projeto_gerador_ideias_backend.repository.IdeaRepository;

import java.util.List;
import java.util.regex.Pattern;

import java.util.stream.Collectors;

@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final WebClient webClient;

    @Value("${ollama.model}")
    private String ollamaModel;

    private static final String REJEICAO_SEGURANCA = "Desculpe, não posso gerar ideias sobre esse tema.";

    private static final Pattern HEADER_CLEANUP_PATTERN = Pattern.compile("(?s)#{2,}.*?(\\R|$)");

    private static final String PROMPT_MODERACAO =
            "Analise o 'Tópico' abaixo. O tópico sugere uma intenção maliciosa, ilegal ou antiética (como phishing, fraude, malware, invasão, etc.)?" +
                    "Responda APENAS 'SEGURO' ou 'PERIGOSO'.\n\n" +
                    "Tópico: \"%s\"\n\n" +
                    "RESPOSTA (SEGURO ou PERIGOSO):";

    private static final String PROMPT_GERACAO =
            "Gere uma ideia concisa (30 palavras ou menos) em português do Brasil sobre o Tópico.\n\n" +
                    "Tópico: \"%s\"\n\n" +
                    "REGRAS OBRIGATÓRIAS:\n" +
                    "1. TAMANHO: 30 palavras ou menos. NÃO liste 10 itens. NÃO escreva roteiros.\n" +
                    "2. FORMATO: Responda APENAS o texto da ideia. NÃO inclua saudações, explicações ou cabeçalhos.\n\n" +
                    "RESPOSTA (MÁX 30 PALAVRAS):";

    public IdeaService(IdeaRepository ideaRepository,
                       WebClient.Builder webClientBuilder,
                       @Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.ideaRepository = ideaRepository;
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
    }

    /**
     * Helper genérico para chamar o Ollama com um modelo e prompt específicos
     */
    private String callOllama(String prompt, String modelName) {
        OllamaRequest ollamaRequest = new OllamaRequest(modelName, prompt);
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


    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request) {
        long startTime = System.currentTimeMillis();
        String userContext = request.getContext();

        String moderationPrompt = String.format(PROMPT_MODERACAO, userContext);
        String moderationResult = callOllama(moderationPrompt, ollamaModel);

        if (moderationResult.contains("PERIGOSO") || !moderationResult.contains("SEGURO")) {

            long executionTime = System.currentTimeMillis() - startTime;

            Idea newIdea = new Idea(
                    request.getTheme(),
                    userContext,
                    REJEICAO_SEGURANCA,
                    ollamaModel,
                    executionTime
            );
            return new IdeaResponse(newIdea);
        }

        String topicoUsuario = String.format("Tema: %s, Contexto: %s",
                request.getTheme().getValue(),
                userContext);

        String generationPrompt = String.format(PROMPT_GERACAO, topicoUsuario);
        String generatedContent = callOllama(generationPrompt, ollamaModel);

        generatedContent = HEADER_CLEANUP_PATTERN.matcher(generatedContent).replaceAll("").trim();

        if (generatedContent.startsWith("Embora seja impossível")) {
            int firstNewline = generatedContent.indexOf('\n');
            if (firstNewline != -1) {
                generatedContent = generatedContent.substring(firstNewline).trim();
            }
        }

        if (generatedContent.startsWith("I cannot") || generatedContent.startsWith("Sorry, I can't")) {
            generatedContent = REJEICAO_SEGURANCA;
        }

        long executionTime = System.currentTimeMillis() - startTime;

        Idea newIdea = new Idea(
                request.getTheme(),
                userContext,
                generatedContent,
                ollamaModel,
                executionTime
        );
        Idea savedIdea = ideaRepository.save(newIdea);
        return new IdeaResponse(savedIdea);
    }

    public List<IdeaResponse> listarHistoricoIdeias() {
        List<Idea> ideias = ideaRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return ideias.stream()
                .map(IdeaResponse::new)
                .collect(Collectors.toList());
    }
}