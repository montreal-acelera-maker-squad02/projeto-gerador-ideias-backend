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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_message_session_id", columnList = "session_id"),
    @Index(name = "idx_chat_message_created_at", columnList = "session_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"session"})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer tokensUsed;

    @Column(nullable = true)
    private Integer tokensRemaining;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum MessageRole {
        USER,
        ASSISTANT
    }

    public ChatMessage(ChatSession session, MessageRole role, String content, Integer tokensUsed) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.tokensUsed = tokensUsed;
    }

    public ChatMessage(ChatSession session, MessageRole role, String content, Integer tokensUsed, Integer tokensRemaining) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.tokensUsed = tokensUsed;
        this.tokensRemaining = tokensRemaining;
    }
}

