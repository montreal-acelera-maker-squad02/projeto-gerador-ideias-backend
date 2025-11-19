package projeto_gerador_ideias_backend.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.request.IdeaRequest;
import projeto_gerador_ideias_backend.dto.response.IdeaResponse;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.UserFavoriteRepository;
import projeto_gerador_ideias_backend.repository.ThemeRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final OllamaCacheableService ollamaService;
    private final FailureCounterService failureCounterService;
    private final ThemeRepository themeRepository;
    private final IdeaSummaryService ideaSummaryService;
    private final UserFavoriteRepository userFavoriteRepository;
    private final IdeasSummaryCacheService ideasSummaryCacheService;
    @Value("${ollama.model}")
    private String ollamaModel;

    private static final String REJEICAO_SEGURANCA = "Desculpe, não posso gerar ideias sobre esse tema.";
    private static final String FIELD_CREATED_AT = "createdAt";

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
                       OllamaCacheableService ollamaService, 
                       FailureCounterService failureCounterService, 
                       ThemeRepository themeRepository,
                       IdeaSummaryService ideaSummaryService, 
                       UserFavoriteRepository userFavoriteRepository,
                       IdeasSummaryCacheService ideasSummaryCacheService) {
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.ollamaService = ollamaService;
        this.failureCounterService = failureCounterService;
        this.themeRepository = themeRepository;
        this.ideaSummaryService = ideaSummaryService;
        this.userFavoriteRepository = userFavoriteRepository;
        this.ideasSummaryCacheService = ideasSummaryCacheService;
    }

    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request, boolean skipCache) {
        User currentUser = getCurrentAuthenticatedUser();

        Theme theme = themeRepository.findById(request.getTheme())
                .orElseThrow(() -> new IllegalArgumentException("Tema inválido: " + request.getTheme()));

        if (!skipCache) {
            Optional<Idea> userSpecificIdea = ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                    currentUser, theme, request.getContext()
            );

            if (userSpecificIdea.isPresent()) {
                return new IdeaResponse(userSpecificIdea.get());
            }
        }

        long startTime = System.currentTimeMillis();

        String aiGeneratedContent;
        try {
            aiGeneratedContent = getCachedAiResponse(theme, request.getContext(), skipCache);
            failureCounterService.resetCounter(currentUser.getEmail());
        } catch (OllamaServiceException e) {
            failureCounterService.handleFailure(currentUser.getEmail(), currentUser.getName());
            throw e;
        }

        long executionTime = System.currentTimeMillis() - startTime;

        if (REJEICAO_SEGURANCA.equals(aiGeneratedContent)) {
            log.warn("Ideia rejeitada pela moderação. Contexto: {}", request.getContext());
            throw new ValidationException(REJEICAO_SEGURANCA);
        }

        Idea newIdea = new Idea(
                theme,
                request.getContext(),
                aiGeneratedContent,
                ollamaModel,
                executionTime
        );
        newIdea.setUser(currentUser);

        String summary = ideaSummaryService.summarizeIdeaSimple(aiGeneratedContent);
        newIdea.setSummary(summary);
        Idea savedIdea = ideaRepository.save(newIdea);
        
        ideasSummaryCacheService.invalidateUserCache(currentUser.getId());

        return new IdeaResponse(savedIdea);
    }

    public String getCachedAiResponse(Theme theme, String context, boolean skipCache) {

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
                theme != null ? theme.getName() : "Tema desconhecido",
                context);

        String generationPrompt = String.format(PROMPT_GERACAO, topicoUsuario);
        String generatedContent;

        if (skipCache) {
            generatedContent = ollamaService.getAiResponseBypassingCache(generationPrompt);
        } else {
            generatedContent = ollamaService.getAiResponse(generationPrompt);
        }

        return cleanUpAiResponse(generatedContent);
    }

    @Transactional
    public IdeaResponse generateSurpriseIdea() {
        User currentUser = getCurrentAuthenticatedUser();
        long startTime = System.currentTimeMillis();

        List<Theme> allThemes = themeRepository.findAll();
        if (allThemes.isEmpty()) {
            throw new IllegalStateException("Nenhum tema disponível para gerar ideia surpresa.");
        }

        Theme randomTheme = allThemes.get(random.nextInt(allThemes.size()));
        String randomType = SURPRISE_TYPES.get(random.nextInt(SURPRISE_TYPES.size()));

        String userContext = String.format("%s sobre %s", randomType, randomTheme.getName());

        String aiContent;
        try {
            String generationPrompt = String.format(PROMPT_SURPRESA, randomType, randomTheme.getName());
            aiContent = ollamaService.getAiResponseBypassingCache(generationPrompt);
            failureCounterService.resetCounter(currentUser.getEmail());
        } catch (OllamaServiceException e) {
            failureCounterService.handleFailure(currentUser.getEmail(), currentUser.getName());
            throw e;
        }

        String finalContent = cleanUpAiResponse(aiContent);
        long executionTime = System.currentTimeMillis() - startTime;

        Idea newIdea = new Idea(
                randomTheme,
                userContext,
                finalContent,
                ollamaModel,
                executionTime
        );
        newIdea.setUser(currentUser);

        String summary = ideaSummaryService.summarizeIdeaSimple(finalContent);
        newIdea.setSummary(summary);

        Idea savedIdea = ideaRepository.save(newIdea);
        
        ideasSummaryCacheService.invalidateUserCache(currentUser.getId());
        
        return new IdeaResponse(savedIdea);
    }

    private String cleanUpAiResponse(String generatedContent) {
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
        } else {
            finalContent = generatedContent;
        }
        return finalContent;
    }

    @Transactional(readOnly = true)
    public Page<IdeaResponse> listarHistoricoIdeiasFiltrado(
            Long userId,
            Long themeId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {

        Theme themeEntity = null;
        if (themeId != null) {
            themeEntity = themeRepository.findById(themeId)
                    .orElseThrow(() -> new IllegalArgumentException("O tema com ID '" + themeId + "' é inválido."));
        }

        final Theme finalThemeEntity = themeEntity;

        Specification<Idea> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            if (userId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("user").get("id"), userId));
            }

            if (finalThemeEntity != null) {
                predicate = cb.and(predicate, cb.equal(root.get("theme"), finalThemeEntity));
            }

            if (startDate != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get(FIELD_CREATED_AT), startDate));
            }

            if (endDate != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get(FIELD_CREATED_AT), endDate));
            }

            query.orderBy(cb.desc(root.get(FIELD_CREATED_AT)));
            return predicate;
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT));

        Page<Idea> ideias = ideaRepository.findAll(spec, pageable);

        if (ideias.isEmpty()) {
            throw new ResourceNotFoundException("Nenhuma ideia encontrada no banco de dados para os filtros informados.");
        }

        return ideias.map(IdeaResponse::new);
    }


    @Transactional(readOnly = true)
    public Page<IdeaResponse> listarMinhasIdeiasPaginadas(
            Long theme,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size
    ) {
        User user = getCurrentAuthenticatedUser();

        Specification<Idea> spec = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("id"), user.getId());

        spec = spec.and(buildIdeaFiltersSpecification(theme, startDate, endDate));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Idea> ideiasPage = ideaRepository.findAll(spec, pageable);

        if (ideiasPage.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma ideia encontrada para o usuário: " + user.getEmail() + " com os filtros aplicados.");
        }

        return ideiasPage.map(IdeaResponse::new);
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
        if (userFavoriteRepository.existsByUserIdAndIdeaId(user.getId(), ideaId)) {
            throw new IllegalArgumentException("Ideia já está favoritada.");
        }
        userFavoriteRepository.addFavorite(user.getId(), idea.getId());
    }

    @Transactional
    public void desfavoritarIdeia(Long ideaId) {
        User user = getCurrentAuthenticatedUser();
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));
        if (!userFavoriteRepository.existsByUserIdAndIdeaId(user.getId(), ideaId)) {
            throw new IllegalArgumentException("Ideia não está favoritada.");
        }

        userFavoriteRepository.deleteById(new projeto_gerador_ideias_backend.model.UserFavorite.UserFavoriteId(user.getId(), idea.getId()));
    }

    @Transactional(readOnly = true)
    public Page<IdeaResponse> listarIdeiasFavoritadasPaginadas(
            Long theme,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size
    ) {
        User user = getCurrentAuthenticatedUser();

        Specification<Idea> spec = (root, query, criteriaBuilder) -> {
            Join<Idea, User> users = root.join("favoritedByUsers");

            return criteriaBuilder.equal(users.get("id"), user.getId());
        };

        spec = spec.and(buildIdeaFiltersSpecification(theme, startDate, endDate));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Idea> favoritasPage = ideaRepository.findAll(spec, pageable);

        if (favoritasPage.isEmpty()) {
            throw new ResourceNotFoundException("Nenhuma ideia favoritada encontrada para este usuário com os filtros aplicados.");
        }

        return favoritasPage.map(IdeaResponse::new);
    }


    @Transactional(readOnly = true)
    public Double getAverageIdeaGenerationTime() {
        User user = getCurrentAuthenticatedUser();
        return ideaRepository.getAverageExecutionTimeForUser(user.getId());
    }

    @Transactional(readOnly = true)
    public long getFavoriteIdeasCount() {
        User user = getCurrentAuthenticatedUser();
        return ideaRepository.countFavoriteIdeasByUserId(user.getId());
    }

    private Specification<Idea> buildIdeaFiltersSpecification(Long theme, LocalDateTime startDate, LocalDateTime endDate) {
        Specification<Idea> spec = Specification.where(null);

        if (theme != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("theme").get("id"), theme));
        }
        if (startDate != null && endDate != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.between(root.get("createdAt"), startDate, endDate));
        } else if (startDate != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        } else if (endDate != null) {

            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }
        return spec;
    }
}
