package projeto_gerador_ideias_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatLogsByDate {
    private String date;
    private Integer totalMessages;
    private Integer totalTokens;
    private List<ChatLogEntry> messages;
}



