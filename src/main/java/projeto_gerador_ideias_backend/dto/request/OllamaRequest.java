package projeto_gerador_ideias_backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class OllamaRequest {
    private static final String ROLE_SYSTEM = "system";
    
    private String model;
    private List<Message> messages;
    private boolean stream = false;
    private Integer numPredict; 
    private Double temperature; 
    private Double topP; 
    private Integer numCtx; 

    public OllamaRequest(String model, String promptDoUsuario) {
        this.model = model;
        this.messages = List.of(new Message("user", promptDoUsuario));
    }

    public OllamaRequest(String model, String systemPrompt, String userPrompt) {
        this.model = model;
        this.messages = List.of(
                new Message(ROLE_SYSTEM, systemPrompt),
                new Message("user", userPrompt)
        );
    }

    public OllamaRequest(String model, String systemPrompt, List<Message> historyMessages, String userPrompt) {
        this.model = model;
        java.util.ArrayList<Message> allMessages = new java.util.ArrayList<>();
        allMessages.add(new Message(ROLE_SYSTEM, systemPrompt));
        if (historyMessages != null && !historyMessages.isEmpty()) {
            allMessages.addAll(historyMessages);
        }
        allMessages.add(new Message("user", userPrompt));
        this.messages = allMessages;
    }
    
    public OllamaRequest(String model, String systemPrompt, List<Message> historyMessages, String userPrompt,
                        OllamaParameters parameters) {
        this.model = model;
        java.util.ArrayList<Message> allMessages = new java.util.ArrayList<>();
        allMessages.add(new Message(ROLE_SYSTEM, systemPrompt));
        if (historyMessages != null && !historyMessages.isEmpty()) {
            allMessages.addAll(historyMessages);
        }
        allMessages.add(new Message("user", userPrompt));
        this.messages = allMessages;
        if (parameters != null) {
            this.numPredict = parameters.numPredict;
            this.temperature = parameters.temperature;
            this.topP = parameters.topP;
            this.numCtx = parameters.numCtx;
        }
    }
    
    public OllamaRequest(String model, String systemPrompt, String userPrompt,
                        Integer numPredict, Double temperature, Double topP, Integer numCtx) {
        this.model = model;
        this.messages = List.of(
                new Message(ROLE_SYSTEM, systemPrompt),
                new Message("user", userPrompt)
        );
        this.numPredict = numPredict;
        this.temperature = temperature;
        this.topP = topP;
        this.numCtx = numCtx;
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

    public static class OllamaParameters {
        private final Integer numPredict;
        private final Double temperature;
        private final Double topP;
        private final Integer numCtx;

        public OllamaParameters(Integer numPredict, Double temperature, Double topP, Integer numCtx) {
            this.numPredict = numPredict;
            this.temperature = temperature;
            this.topP = topP;
            this.numCtx = numCtx;
        }

        public Integer getNumPredict() {
            return numPredict;
        }

        public Double getTemperature() {
            return temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public Integer getNumCtx() {
            return numCtx;
        }
    }
}
