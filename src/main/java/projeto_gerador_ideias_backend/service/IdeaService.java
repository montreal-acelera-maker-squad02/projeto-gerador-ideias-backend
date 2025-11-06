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
import projeto_gerador_ideias_backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

@Service
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final WebClient webClient;

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
                       WebClient.Builder webClientBuilder,
                       @Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.ideaRepository = ideaRepository;
        this.userRepository = userRepository;
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
    }

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
                throw new OllamaServiceException("Resposta nula ou inválida do Ollama (/api/chat).");
            }
        } catch (OllamaServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaServiceException("Erro ao se comunicar com a IA (Ollama): " + e.getMessage(), e);
        }
    }


    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        long startTime = System.currentTimeMillis();
        String userContext = request.getContext();

        String moderationPrompt = String.format(PROMPT_MODERACAO, userContext);
        String moderationResult = callOllama(moderationPrompt, ollamaModel);

        if (moderationResult.contains("PERIGOSO")) {
            long executionTime = System.currentTimeMillis() - startTime;
            Idea newIdea = new Idea(
                    request.getTheme(), userContext, REJEICAO_SEGURANCA,
                    ollamaModel, executionTime
            );
            newIdea.setUser(currentUser);
            return new IdeaResponse(newIdea);

        } else if (!moderationResult.contains("SEGURO")) {
            throw new OllamaServiceException("Falha na moderação: A IA retornou uma resposta inesperada. Tente novamente em alguns segundos.");
        }

        String topicoUsuario = String.format("Tema: %s, Contexto: %s",
                request.getTheme().getValue(),
                userContext);

        String generationPrompt = String.format(PROMPT_GERACAO, topicoUsuario);

        return runGenerationAndSave(
                currentUser,
                request.getTheme(),
                userContext,
                generationPrompt,
                startTime,
                false
        );
    }

    @Transactional
    public IdeaResponse generateSurpriseIdea() {
        User currentUser = getCurrentAuthenticatedUser();
        long startTime = System.currentTimeMillis();

        Theme randomTheme = Theme.values()[random.nextInt(Theme.values().length)];
        String randomType = SURPRISE_TYPES.get(random.nextInt(SURPRISE_TYPES.size()));

        String userContext = String.format("%s sobre %s", randomType, randomTheme.getValue());

        String generationPrompt = String.format(PROMPT_SURPRESA, randomType, randomTheme.getValue());

        return runGenerationAndSave(
                currentUser,
                randomTheme,
                userContext,
                generationPrompt,
                startTime,
                true
        );
    }

    private IdeaResponse runGenerationAndSave(User user, Theme theme, String context, String generationPrompt, long startTime, boolean isSurprise) {

        String generatedContent = callOllama(generationPrompt, ollamaModel);

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

        long executionTime = System.currentTimeMillis() - startTime;

        Idea newIdea = new Idea(
                theme,
                context,
                finalContent,
                ollamaModel,
                executionTime
        );
        newIdea.setUser(user);
        Idea savedIdea = ideaRepository.save(newIdea);
        return new IdeaResponse(savedIdea);
    }

    @Transactional(readOnly = true)
    public List<IdeaResponse> listarHistoricoIdeiasFiltrado(String theme, LocalDateTime startDate, LocalDateTime endDate) {
        List<Idea> ideias;

        if (theme != null && startDate != null && endDate != null) {
            ideias = ideaRepository.findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(Theme.valueOf(theme.toUpperCase()), startDate, endDate);
        }
        else if (theme != null) {
            ideias = ideaRepository.findByThemeOrderByCreatedAtDesc(Theme.valueOf(theme.toUpperCase()));
        }
        else if (startDate != null && endDate != null) {
            ideias = ideaRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
        }
        else {
            ideias = ideaRepository.findAllByOrderByCreatedAtDesc();
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

        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado no banco de dados: " + userEmail));
    }

    // FAVORITAR IDEIA
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


    // DESFAVORITAR IDEIA
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
}