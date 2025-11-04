package projeto_gerador_ideias_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.ChatSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByUserIdAndType(Long userId, ChatSession.ChatType type);
    Optional<ChatSession> findByUserIdAndIdeaId(Long userId, Long ideaId);
    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}

