package projeto_gerador_ideias_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
import projeto_gerador_ideias_backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final OllamaCacheableService ollamaService;

    @Value("${ollama.model}")
    private String ollamaModel;

    private static final String REJEICAO_SEGURANCA = "Desculpe, não posso gerar ideias sobre esse tema.";

    private static final Pattern HEADER_CLEANUP_PATTERN = Pattern.compile("(?s)#{2,}.*?(\\R|$)");

    private static final List<String> SURPRISE_TYPES = List.of(
            "um nome de startup",
            "um slogan de marketing",
            "uma ideia de produto",
            "um post para redes sociais"
    );
    private final Random random = new Random();

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

    private static final String PROMPT_SURPRESA =
            "Gere %s sobre o tema %s. Seja criativo e direto (máximo 30 palavras) em português do Brasil.\n\n" +
                    "REGRAS OBRIGATÓRIAS:\n" +
                    "1. FORMATO: Responda APENAS a ideia. \n" +
                    "2. NÃO inclua saudações, explicações, cabeçalhos ou o tema na resposta.\n\n" +
                    "RESPOSTA (APENAS A IDEIA):";

    public IdeaService(IdeaRepository ideaRepository,
                       UserRepository userRepository,
                       OllamaCacheableService ollamaService) {
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.ollamaService = ollamaService;
    }

    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request) {
        User currentUser = getCurrentAuthenticatedUser();

        Optional<Idea> userSpecificIdea = ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                currentUser, request.getTheme(), request.getContext()
        );

        if (userSpecificIdea.isPresent()) {
            return new IdeaResponse(userSpecificIdea.get());
        }

        long startTime = System.currentTimeMillis();
        String aiGeneratedContent = getCachedAiResponse(request.getTheme(), request.getContext());

        long executionTime = System.currentTimeMillis() - startTime;

        Idea newIdea = new Idea(
                request.getTheme(),
                request.getContext(),
                aiGeneratedContent,
                ollamaModel,
                executionTime
        );
        newIdea.setUser(currentUser);
        Idea savedIdea = ideaRepository.save(newIdea);

        return new IdeaResponse(savedIdea);
    }

    @Cacheable(value = "aiResponseCache", key = "#theme.name() + '-' + #context")
    public String getCachedAiResponse(Theme theme, String context) {

        String moderationPrompt = String.format(PROMPT_MODERACAO, context);
        String moderationResult = ollamaService.getAiResponse(moderationPrompt);

        if (moderationResult.contains("PERIGOSO")) {
            return REJEICAO_SEGURANCA;
        }

        String topicoUsuario = String.format("Tema: %s, Contexto: %s",
                theme.getValue(),
                context);
        String generationPrompt = String.format(PROMPT_GERACAO, topicoUsuario);
        String generatedContent = ollamaService.getAiResponse(generationPrompt);

        return cleanUpAiResponse(generatedContent, context, false);
    }

    @Transactional
    public IdeaResponse generateSurpriseIdea() {
        User currentUser = getCurrentAuthenticatedUser();
        long startTime = System.currentTimeMillis();

        Theme randomTheme = Theme.values()[random.nextInt(Theme.values().length)];
        String randomType = SURPRISE_TYPES.get(random.nextInt(SURPRISE_TYPES.size()));
        String userContext = String.format("%s sobre %s", randomType, randomTheme.getValue());

        String generationPrompt = String.format(PROMPT_SURPRESA, randomType, randomTheme.getValue());

        String aiContent = ollamaService.getAiResponse(generationPrompt);
        String finalContent = cleanUpAiResponse(aiContent, userContext, true);

        long executionTime = System.currentTimeMillis() - startTime;

        Idea newIdea = new Idea(
                randomTheme,
                userContext,
                finalContent,
                ollamaModel,
                executionTime
        );
        newIdea.setUser(currentUser);
        Idea savedIdea = ideaRepository.save(newIdea);
        return new IdeaResponse(savedIdea);
    }

    private String cleanUpAiResponse(String generatedContent, String context, boolean isSurprise) {
        generatedContent = HEADER_CLEANUP_PATTERN.matcher(generatedContent).replaceAll("").trim();

        if (generatedContent.startsWith("Embora seja impossível")) {
            int firstNewline = generatedContent.indexOf('\n');
            if (firstNewline != -1) {
                generatedContent = generatedContent.substring(firstNewline).trim();
            }
        }

        if (generatedContent.startsWith("\"") && generatedContent.endsWith("\"") && generatedContent.length() > 2) {
            generatedContent = generatedContent.substring(1, generatedContent.length() - 1);
        }

        String finalContent;
        if (generatedContent.startsWith("I cannot") || generatedContent.startsWith("Sorry, I can't") || generatedContent.isEmpty()) {
            finalContent = REJEICAO_SEGURANCA;
        } else if (isSurprise) {
            finalContent = String.format("%s: %s", context, generatedContent);
        } else {
            finalContent = generatedContent;
        }
        return finalContent;
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

    /**
     * Busca o usuário autenticado no contexto de segurança.
     */
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new ResourceNotFoundException("Usuário não autenticado. Não é possível gerar ideias.");
        }

        String userEmail = ((UserDetails) authentication.getPrincipal()).getUsername();

        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado no banco de dados: " + userEmail));
    }

}