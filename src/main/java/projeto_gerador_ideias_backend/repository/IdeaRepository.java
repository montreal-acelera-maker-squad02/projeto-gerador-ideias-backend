package projeto_gerador_ideias_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Long>, JpaSpecificationExecutor<Idea> {

    List<Idea> findAllByOrderByCreatedAtDesc();

    List<Idea> findByThemeOrderByCreatedAtDesc(Theme theme);

    List<Idea> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<Idea> findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(Theme theme, LocalDateTime start, LocalDateTime end);

    List<Idea> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    @Query("SELECT i FROM Idea i " +
           "LEFT JOIN FETCH i.theme " +
           "WHERE i.user.id = :userId " +
           "ORDER BY i.createdAt DESC")
    List<Idea> findByUserIdWithThemeOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    @Query("SELECT i.id, i.summary, i.theme.name, i.createdAt FROM Idea i " +
           "WHERE i.user.id = :userId " +
           "ORDER BY i.createdAt DESC")
    List<Object[]> findIdeasSummaryOnlyByUserId(@Param("userId") Long userId);

    Optional<Idea> findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(User user, Theme theme, String context);
}