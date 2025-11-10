package projeto_gerador_ideias_backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class OllamaRequest {
    private String model;
    private List<Message> messages;
    private boolean stream = false;

    public OllamaRequest(String model, String promptDoUsuario) {
        this.model = model;
        this.messages = List.of(new Message("user", promptDoUsuario));
    }

    public OllamaRequest(String model, String systemPrompt, String userPrompt) {
        this.model = model;
        this.messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        );
    }

    public OllamaRequest(String model, String systemPrompt, List<Message> historyMessages, String userPrompt) {
        this.model = model;
        java.util.ArrayList<Message> allMessages = new java.util.ArrayList<>();
        allMessages.add(new Message("system", systemPrompt));
        if (historyMessages != null && !historyMessages.isEmpty()) {
            allMessages.addAll(historyMessages);
        }
        allMessages.add(new Message("user", userPrompt));
        this.messages = allMessages;
    }

    @Data
    @NoArgsConstructor
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
