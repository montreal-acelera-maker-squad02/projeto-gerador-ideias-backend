package projeto_gerador_ideias_backend.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final OllamaCacheableService ollamaService;
    private final FailureCounterService failureCounterService;

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

    private static final String PROMPT_MODERACAO = """
            Analise o 'Tópico' abaixo. O tópico sugere uma intenção maliciosa, ilegal ou antiética (como phishing, fraude, malware, invasão, etc.)?
            Responda APENAS 'SEGURO' ou 'PERIGOSO'.
            
            Tópico: "%s"
            
            RESPOSTA (SEGURO ou PERIGOSO):""";

    private static final String PROMPT_GERACAO = """
            Gere uma ideia concisa (30 palavras ou menos) em português do Brasil sobre o Tópico.
            
            Tópico: "%s"
            
            REGRAS OBRIGATÓRIAS:
            1. TAMANHO: 30 palavras ou menos. NÃO liste 10 itens. NÃO escreva roteiros.
            2. FORMATO: Responda APENAS o texto da ideia. NÃO inclua saudações, explicações ou cabeçalhos.
            
            RESPOSTA (MÁX 30 PALAVRAS):""";

    private static final String PROMPT_SURPRESA = """
            Gere %s sobre o tema %s. Seja criativo e direto (máximo 30 palavras) em português do Brasil.
            
            REGRAS OBRIGATÓRIAS:
            1. FORMATO: Responda APENAS a ideia. 
            2. NÃO inclua saudações, explicações, cabeçalhos ou o tema na resposta.
            
            RESPOSTA (APENAS A IDEIA):""";

    public IdeaService(IdeaRepository ideaRepository,
                       UserRepository userRepository,
                       OllamaCacheableService ollamaService, FailureCounterService failureCounterService) {
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.ollamaService = ollamaService;
        this.failureCounterService = failureCounterService;
    }

    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request, boolean skipCache) {
        User currentUser = getCurrentAuthenticatedUser();

        if (!skipCache) {
            Optional<Idea> userSpecificIdea = ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                    currentUser, request.getTheme(), request.getContext()
            );

            if (userSpecificIdea.isPresent()) {
                return new IdeaResponse(userSpecificIdea.get());
            }
        }

        long startTime = System.currentTimeMillis();

        String aiGeneratedContent;
        try {
            aiGeneratedContent = getCachedAiResponse(request.getTheme(), request.getContext(), skipCache, currentUser);
            failureCounterService.resetCounter(currentUser.getEmail());
        } catch (OllamaServiceException e) {
            failureCounterService.handleFailure(currentUser.getEmail(), currentUser.getName());
            throw e;
        }

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

    /**
     * Este método decide se deve usar o cache técnico ou ir direto para a IA.
     */
    public String getCachedAiResponse(Theme theme, String context, boolean skipCache, User user) {

        String moderationPrompt = String.format(PROMPT_MODERACAO, context);
        String moderationResult;

        if (skipCache) {
            moderationResult = ollamaService.getAiResponseBypassingCache(moderationPrompt);
        } else {
            moderationResult = ollamaService.getAiResponse(moderationPrompt);
        }

        if (moderationResult.contains("PERIGOSO")) {
            return REJEICAO_SEGURANCA;
        }

        String topicoUsuario = String.format("Tema: %s, Contexto: %s",
                theme.getValue(),
                context);
        String generationPrompt = String.format(PROMPT_GERACAO, topicoUsuario);
        String generatedContent;

        if (skipCache) {
            generatedContent = ollamaService.getAiResponseBypassingCache(generationPrompt);
        } else {
            generatedContent = ollamaService.getAiResponse(generationPrompt);
        }

        return cleanUpAiResponse(generatedContent, context, false);
    }

    @Transactional
    public IdeaResponse generateSurpriseIdea() {
        User currentUser = getCurrentAuthenticatedUser();
        long startTime = System.currentTimeMillis();

        Theme randomTheme = Theme.values()[random.nextInt(Theme.values().length)];
        String randomType = SURPRISE_TYPES.get(random.nextInt(SURPRISE_TYPES.size()));
        String userContext = String.format("%s sobre %s", randomType, randomTheme.getValue());

        String aiContent;
        try {
            String generationPrompt = String.format(PROMPT_SURPRESA, randomType, randomTheme.getValue());
            aiContent = ollamaService.getAiResponseBypassingCache(generationPrompt);
            failureCounterService.resetCounter(currentUser.getEmail());
        } catch (OllamaServiceException e) {
            failureCounterService.handleFailure(currentUser.getEmail(), currentUser.getName());
            throw e;
        }

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

    @Transactional(readOnly = true)
    public List<IdeaResponse> listarHistoricoIdeiasFiltrado(Long userId, String theme, LocalDateTime startDate,
            LocalDateTime endDate) {

        List<Idea> ideias;

        if (userId != null) {
            Specification<Idea> spec = (root, query, criteriaBuilder) -> {
                Predicate predicate = criteriaBuilder.conjunction();

                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("user").get("id"), userId));

                if (theme != null && !theme.isBlank()) {
                    try {
                        predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("theme"), Theme.valueOf(theme.toUpperCase())));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("O tema '" + theme + "' é inválido.");
                    }
                }
                if (startDate != null) {
                    predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
                }
                if (endDate != null) {
                    predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
                }
                query.orderBy(criteriaBuilder.desc(root.get("createdAt")));
                return predicate;
            };

            ideias = ideaRepository.findAll(spec);
        } else {
            boolean hasTheme = theme != null && !theme.isBlank();
            boolean hasStartEnd = startDate != null && endDate != null;

            if (hasTheme && hasStartEnd) {
                Theme parsedTheme;
                try {
                    parsedTheme = Theme.valueOf(theme.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("O tema '" + theme + "' é inválido.");
                }
                ideias = ideaRepository.findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(parsedTheme, startDate, endDate);
            } else if (hasTheme) {
                Theme parsedTheme;
                try {
                    parsedTheme = Theme.valueOf(theme.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("O tema '" + theme + "' é inválido.");
                }
                ideias = ideaRepository.findByThemeOrderByCreatedAtDesc(parsedTheme);
            } else if (hasStartEnd) {
                ideias = ideaRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
            } else {
                ideias = ideaRepository.findAllByOrderByCreatedAtDesc();
            }
        }

        if (ideias.isEmpty()) {
            throw new ResourceNotFoundException("Nenhuma ideia encontrada no banco de dados para os filtros informados.");
        }

        return ideias.stream().map(IdeaResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public List<IdeaResponse> listarMinhasIdeias() {

        User user = getCurrentAuthenticatedUser();
        List<Idea> ideias = ideaRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        if (ideias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma ideia encontrada para o usuário: " + user.getEmail());
        }


        return ideias.stream().map(IdeaResponse::new).toList();
    }

    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new ResourceNotFoundException("Usuário não autenticado. Não é possível gerar ideias.");
        }

        String userEmail = ((UserDetails) authentication.getPrincipal()).getUsername();
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado no banco de dados: " + userEmail));
        
        log.debug("User authenticated - Email: {}, User found: id={}, name={}, email={}", 
            userEmail, user.getId(), user.getName(), user.getEmail());
        
        return user;
    }

    @Transactional
    public void favoritarIdeia(Long ideaId) {
        User user = getCurrentAuthenticatedUser();
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));
        if (user.getFavoriteIdeas().contains(idea)) {
            throw new IllegalArgumentException("Ideia já está favoritada.");
        }
        user.getFavoriteIdeas().add(idea);
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public void desfavoritarIdeia(Long ideaId) {
        User user = getCurrentAuthenticatedUser();
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));
        if (!user.getFavoriteIdeas().contains(idea)) {
            throw new IllegalArgumentException("Ideia não está favoritada.");
        }

        user.getFavoriteIdeas().remove(idea);

        userRepository.saveAndFlush(user);
    }

    // BUSCAR IDEIAS FAVORITAS
    @Transactional(readOnly = true)
    public List<IdeaResponse> listarIdeiasFavoritadas() {
        User user = getCurrentAuthenticatedUser();

        Set<Idea> favoritas = user.getFavoriteIdeas();

        if (favoritas == null || favoritas.isEmpty()) {
            throw new ResourceNotFoundException("Nenhuma ideia favoritada encontrada para este usuário.");
        }

        return favoritas.stream()
                .map(IdeaResponse::new)
                .toList();
    }
}