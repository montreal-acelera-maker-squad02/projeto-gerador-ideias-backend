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
    private static final List<String> BLACKLIST_PALAVRAS = List.of(
            "phishing", "malware", "keylogger", "vírus", "trojan",
            "roubar senha", "fraudar", "veneno", "exploit", "ciberbullying", "antiético",
            "invadir", "ransomware", "ddos"
    );
    private static final Pattern SECURITY_BLACKLIST_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", BLACKLIST_PALAVRAS) + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HEADER_CLEANUP_PATTERN = Pattern.compile("(?s)#{2,}.*?(\\R|$)");

    public IdeaService(IdeaRepository ideaRepository,
                       WebClient.Builder webClientBuilder,
                       @Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.ideaRepository = ideaRepository;
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
    }

    @Transactional
    public IdeaResponse generateIdea(IdeaRequest request) {
        long startTime = System.currentTimeMillis();
        String userContext = request.getContext();

        if (userContext != null && SECURITY_BLACKLIST_PATTERN.matcher(userContext).find()) {

            long executionTime = System.currentTimeMillis() - startTime;

            Idea newIdea = new Idea(
                    request.getTheme(),
                    userContext,
                    REJEICAO_SEGURANCA,
                    "local-security-filter",
                    executionTime
            );
            return new IdeaResponse(newIdea);
        }

        String topicoUsuario = String.format("Tema: %s, Contexto: %s",
                request.getTheme().getValue(),
                userContext);

        String promptMestre = String.format(
                "Gere uma ideia concisa (50 palavras ou menos) em português do Brasil sobre o Tópico.\n\n" +
                        "Tópico: \"%s\"\n\n" +
                        "REGRAS OBRIGATÓRIAS:\n" +
                        "1. SEGURANÇA: Se o Tópico sugerir uma ação com intenção maliciosa ou ilegal (como fraude ou invasão), " +
                        "responda APENAS: \"%s\". Tópicos históricos SÃO PERMITIDOS.\n" +
                        "2. TAMANHO: 50 palavras ou menos.\n" +
                        "3. FORMATO: Responda APENAS o texto da ideia. NÃO inclua saudações, explicações ou cabeçalhos.\n\n" +
                        "RESPOSTA:",
                topicoUsuario, REJEICAO_SEGURANCA
        );

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

                String generatedContent = ollamaResponse.getMessage().getContent().trim();

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

                Idea newIdea = new Idea(
                        request.getTheme(),
                        userContext,
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

    public List<IdeaResponse> listarHistoricoIdeias() {
        List<Idea> ideias = ideaRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return ideias.stream()
                .map(IdeaResponse::new)
                .collect(Collectors.toList());
    }
}