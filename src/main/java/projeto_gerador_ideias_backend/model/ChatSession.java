package projeto_gerador_ideias_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions", indexes = {
    @Index(name = "idx_chat_session_user_id", columnList = "user_id"),
    @Index(name = "idx_chat_session_idea_id", columnList = "idea_id"),
    @Index(name = "idx_chat_session_user_type", columnList = "user_id,type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(nullable = false)
    private Integer tokensUsed = 0;

    @Column(nullable = false)
    private LocalDateTime lastResetAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
        this.tokensUsed = 0;
        this.lastResetAt = LocalDateTime.now();
    }
}

