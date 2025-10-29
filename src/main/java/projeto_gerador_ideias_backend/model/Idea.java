package projeto_gerador_ideias_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ideas")
@Data
@NoArgsConstructor
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Theme theme;

    @Column(nullable = false)
    private String context;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String generatedContent;

    @Column(nullable = false)
    private String modelUsed;

    @Column(nullable = false)
    private Long executionTimeMs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Idea(Theme theme, String context, String generatedContent, String modelUsed, Long executionTimeMs) {
        this.theme = theme;
        this.context = context;
        this.generatedContent = generatedContent;
        this.modelUsed = modelUsed;
        this.executionTimeMs = executionTimeMs;
    }
}