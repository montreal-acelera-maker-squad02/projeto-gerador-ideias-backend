package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.model.ChatMessage;
import projeto_gerador_ideias_backend.model.ChatSession;
import projeto_gerador_ideias_backend.model.Idea;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptBuilderService {

    private final PromptSanitizer promptSanitizer;

    private static final String SYSTEM_PROMPT_CHAT_LIVRE = """
            Você é um assistente útil. Responda de forma concisa em português.
            
            IMPORTANTE: Se a mensagem sugerir conteúdo malicioso, ilegal ou antiético, responda APENAS: "[MODERACAO: PERIGOSO]"
            
            Caso contrário, responda normalmente.""";

    private static final String PROMPT_CHAT_COM_IDEIA = """
            Você está conversando sobre esta ideia:
            
            Ideia: "%s"
            Contexto: "%s"
            
            IMPORTANTE: Responda APENAS perguntas relacionadas a esta ideia.
            Se a mensagem NÃO estiver relacionada, responda EXATAMENTE: [MODERACAO: PERIGOSO]
            
            Mantenha respostas concisas (máximo 100 palavras).""";

    public String buildSystemPromptForFreeChat() {
        return SYSTEM_PROMPT_CHAT_LIVRE;
    }

    public String buildSystemPromptForIdeaChat(ChatSession session) {
        if (session.getType() == ChatSession.ChatType.IDEA_BASED) {
            String content = session.getCachedIdeaContent();
            String context = session.getCachedIdeaContext();
            
            if (content == null || context == null) {
                if (session.getIdea() != null) {
                    Idea idea = session.getIdea();
                    content = idea.getGeneratedContent();
                    context = idea.getContext();
                } else {
                    return "Você é um assistente útil e criativo. Responda de forma concisa e em português do Brasil.";
                }
            }
            
            String sanitizedContent = promptSanitizer.escapeForFormat(content);
            String sanitizedContext = promptSanitizer.escapeForFormat(context);
            return String.format(PROMPT_CHAT_COM_IDEIA, sanitizedContent, sanitizedContext);
        } else {
            return "Você é um assistente útil e criativo. Responda de forma concisa e em português do Brasil.";
        }
    }

    public List<projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message> buildMessageHistory(List<ChatMessage> previousMessages) {
        if (previousMessages == null || previousMessages.isEmpty()) {
            return List.of();
        }
        
        List<ChatMessage> validMessages = previousMessages.stream()
                .filter(msg -> msg != null && 
                             msg.getContent() != null && 
                             !msg.getContent().trim().isEmpty())
                .toList();
        
        if (validMessages.isEmpty()) {
            return List.of();
        }
        
        int messagesToInclude = Math.min(3, validMessages.size());
        List<ChatMessage> lastMessages = validMessages.size() > messagesToInclude
                ? validMessages.subList(validMessages.size() - messagesToInclude, validMessages.size())
                : validMessages;
        
        List<ChatMessage> sortedMessages = new java.util.ArrayList<>(lastMessages);
        sortedMessages.sort((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));
        
        return sortedMessages.stream()
                .map(msg -> {
                    String role = msg.getRole() == ChatMessage.MessageRole.USER ? "user" : "assistant";
                    String sanitizedContent = promptSanitizer.sanitizeForPrompt(msg.getContent());
                    return new projeto_gerador_ideias_backend.dto.request.OllamaRequest.Message(role, sanitizedContent);
                })
                .toList();
    }
}

