package projeto_gerador_ideias_backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.ChatSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByUserIdAndType(Long userId, ChatSession.ChatType type);
    
    @Query("SELECT s FROM ChatSession s " +
           "WHERE s.user.id = :userId AND s.idea.id = :ideaId " +
           "ORDER BY s.createdAt DESC")
    List<ChatSession> findByUserIdAndIdeaIdOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("ideaId") Long ideaId);
    
    default Optional<ChatSession> findByUserIdAndIdeaId(Long userId, Long ideaId) {
        List<ChatSession> sessions = findByUserIdAndIdeaIdOrderByCreatedAtDesc(userId, ideaId);
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(0));
    }
    
    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM ChatSession s " +
           "WHERE s.id = :id")
    Optional<ChatSession> findByIdWithLock(@Param("id") Long id);
    
    @Query("SELECT s FROM ChatSession s " +
           "LEFT JOIN FETCH s.user " +
           "LEFT JOIN FETCH s.idea i " +
           "WHERE s.id = :id")
    Optional<ChatSession> findByIdWithIdea(@Param("id") Long id);
}

