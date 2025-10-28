package projeto_gerador_ideias_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdeaResponse {
    private String theme;
    private String content;
    private LocalDateTime createdAt;
}