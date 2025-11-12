package projeto_gerador_ideias_backend.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "ideas", indexes = {
        @Index(name = "idx_idea_user_lookup", columnList = "user_id, theme_id, context")
})
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"user"})
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String context;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    @Basic(fetch = FetchType.EAGER)
    private String generatedContent;

    @Column(nullable = false)
    private String modelUsed;

    @Column(nullable = false)
    private Long executionTimeMs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_id", nullable = false)
    private Theme theme;

    @Column(length = 200)
    private String summary;

    public Idea(Theme theme, String context, String generatedContent, String modelUsed, Long executionTimeMs) {
        this.theme = theme;
        this.context = context;
        this.generatedContent = generatedContent;
        this.modelUsed = modelUsed;
        this.executionTimeMs = executionTimeMs;
    }
}