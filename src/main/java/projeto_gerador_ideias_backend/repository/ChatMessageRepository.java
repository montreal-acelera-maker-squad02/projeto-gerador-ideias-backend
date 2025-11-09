package projeto_gerador_ideias_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    
    long countBySessionId(Long sessionId);
    
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "AND m.role = :role " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findUserMessagesBySessionId(@Param("sessionId") Long sessionId, 
                                                   @Param("role") projeto_gerador_ideias_backend.model.ChatMessage.MessageRole role);
    
    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId AND m.role = :role")
    int getTotalUserTokensBySessionId(@Param("sessionId") Long sessionId, 
                                      @Param("role") projeto_gerador_ideias_backend.model.ChatMessage.MessageRole role);
    
    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM ChatMessage m " +
           "JOIN m.session s " +
           "WHERE s.user.id = :userId AND m.role = :role")
    int getTotalUserTokensByUserId(@Param("userId") Long userId, 
                                    @Param("role") projeto_gerador_ideias_backend.model.ChatMessage.MessageRole role);
    
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "ORDER BY m.createdAt DESC")
    org.springframework.data.domain.Page<ChatMessage> findLastMessagesBySessionId(@Param("sessionId") Long sessionId, 
                                                                                  org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.session s " +
           "LEFT JOIN FETCH s.idea i " +
           "WHERE s.user.id = :userId " +
           "AND m.createdAt >= :startDate " +
           "AND m.createdAt < :endDate " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findByUserIdAndDateRange(@Param("userId") Long userId, 
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.session s " +
           "LEFT JOIN FETCH s.idea i " +
           "WHERE s.user.id = :userId " +
           "AND m.createdAt >= :startDate " +
           "AND m.createdAt < :endDate " +
           "ORDER BY m.createdAt ASC")
    org.springframework.data.domain.Page<ChatMessage> findByUserIdAndDateRangePaginated(
            @Param("userId") Long userId, 
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            org.springframework.data.domain.Pageable pageable);
    
    @Query(value = "SELECT * FROM (" +
           "  SELECT m.*, ROW_NUMBER() OVER (ORDER BY m.created_at DESC) as rn " +
           "  FROM chat_messages m " +
           "  WHERE m.session_id = :sessionId" +
           ") ranked " +
           "WHERE ranked.rn <= :limit " +
           "ORDER BY ranked.created_at ASC", nativeQuery = true)
    List<ChatMessage> findRecentMessagesOptimized(@Param("sessionId") Long sessionId, @Param("limit") int limit);
    
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "AND m.role = :role " +
           "ORDER BY m.createdAt DESC")
    org.springframework.data.domain.Page<ChatMessage> findLastMessagesBySessionIdAndRole(@Param("sessionId") Long sessionId, 
                                                          @Param("role") projeto_gerador_ideias_backend.model.ChatMessage.MessageRole role,
                                                          org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "AND m.role = :role " +
           "AND m.tokensRemaining IS NOT NULL " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findLastAssistantMessageWithTokensRemaining(@Param("sessionId") Long sessionId,
                                                                    @Param("role") projeto_gerador_ideias_backend.model.ChatMessage.MessageRole role);
    
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.session.id = :sessionId " +
           "AND m.createdAt < :beforeTimestamp " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findMessagesBeforeTimestamp(@Param("sessionId") Long sessionId,
                                                   @Param("beforeTimestamp") LocalDateTime beforeTimestamp,
                                                   org.springframework.data.domain.Pageable pageable);
}

