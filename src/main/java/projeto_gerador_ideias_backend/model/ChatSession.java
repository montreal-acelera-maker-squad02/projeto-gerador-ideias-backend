package projeto_gerador_ideias_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions", indexes = {
    @Index(name = "idx_chat_session_user_id", columnList = "user_id"),
    @Index(name = "idx_chat_session_idea_id", columnList = "idea_id"),
    @Index(name = "idx_chat_session_user_type", columnList = "user_id,type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"user", "idea"})
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idea_id")
    private Idea idea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatType type;

    /**
     * @deprecated Este campo está deprecated. Use o cálculo de tokens baseado em ChatMessage ao invés.
     */
    @Column(nullable = false)
    @Deprecated(since = "1.0", forRemoval = true)
    private Integer tokensUsed = 0;

    @Column(nullable = false)
    private LocalDateTime lastResetAt;

    @Column(columnDefinition = "TEXT")
    private String cachedIdeaContent;

    @Column(columnDefinition = "TEXT")
    private String cachedIdeaContext;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = true)
    private Long version;

    @PostLoad
    private void ensureVersion() {
        if (this.version == null) {
            this.version = 0L;
        }
    }
    
    @jakarta.persistence.PrePersist
    private void prePersist() {
        if (this.version == null) {
            this.version = 0L;
        }
    }

    public enum ChatType {
        IDEA_BASED,  
        FREE
    }

    public ChatSession(User user, ChatType type, Idea idea) {
        if (user == null) {
            throw new IllegalArgumentException("User não pode ser null");
        }
        if (type == null) {
            throw new IllegalArgumentException("ChatType não pode ser null");
        }
        if (type == ChatType.IDEA_BASED && idea == null) {
            throw new IllegalArgumentException("ChatType IDEA_BASED requer uma Idea não-null");
        }
        this.user = user;
        this.type = type;
        this.idea = idea;
        this.lastResetAt = LocalDateTime.now();
        this.version = 0L;
        
        if (type == ChatType.IDEA_BASED) {
            this.cachedIdeaContent = idea.getGeneratedContent();
            this.cachedIdeaContext = idea.getContext();
        }
    }
}

