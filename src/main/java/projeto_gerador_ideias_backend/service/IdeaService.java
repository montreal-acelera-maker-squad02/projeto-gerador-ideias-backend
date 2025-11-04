package projeto_gerador_ideias_backend.service;

import lombok.AllArgsConstructor;
import lombok.Data;
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
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import java.util.stream.Collectors;

@Service
@Data
@AllArgsConstructor
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final WebClient webClient;
    private final UserRepository userRepository;

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
                       @Value("${ollama.base-url}") String ollamaBaseUrl, UserRepository userRepository) {
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
    }

    public IdeaService(IdeaRepository ideaRepository, WebClient.Builder webClientBuilder, String baseUrl, IdeaRepository ideaRepository1, WebClient webClient, UserRepository userRepository) {
        this.ideaRepository = ideaRepository1;
        this.webClient = webClient;
        this.userRepository = userRepository;
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

    public List<IdeaResponse> listarHistoricoIdeiasFiltrado(String theme, LocalDateTime startDate, LocalDateTime endDate) {
        List<Idea> ideias;

        // Pesquisa com todos filtros
        if (theme != null && startDate != null && endDate != null) {
            ideias = ideaRepository.findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(Theme.valueOf(theme.toUpperCase()), startDate, endDate);
        }
        // Pesquisa com tema
        else if (theme != null) {
            ideias = ideaRepository.findByThemeOrderByCreatedAtDesc(Theme.valueOf(theme.toUpperCase()));
        }
        // Pesquisa Pesquisa com a data
        else if (startDate != null && endDate != null) {
            ideias = ideaRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
        }
        // Pesquisa sem filtros
        else {
            ideias = ideaRepository.findAllByOrderByCreatedAtDesc();
        }

        return ideias.stream().map(IdeaResponse::new).toList();
    }

    public List<IdeaResponse> listarIdeiasPorUsuario(Long userId) {
        List<Idea> ideias = ideaRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (ideias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma ideia encontrada para o usuário com ID: " + userId);
        }
        return ideias.stream().map(IdeaResponse::new).toList();
    }

    public void favoritarIdeia(Long userId, Long ideaId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));

        user.getFavoriteIdeas().add(idea);
        userRepository.save(user);
    }

    public void desfavoritarIdeia(Long userId, Long ideaId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));

        user.getFavoriteIdeas().remove(idea);
        userRepository.save(user);
    }
}