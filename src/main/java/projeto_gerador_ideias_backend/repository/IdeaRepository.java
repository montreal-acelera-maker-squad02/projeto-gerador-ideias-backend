package projeto_gerador_ideias_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Long>, JpaSpecificationExecutor<Idea> {

    Page<Idea> findByUserId(Long userId, Pageable pageable);

    List<Idea> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "theme"})
    Page<Idea> findAll(Specification<Idea> spec, Pageable pageable);
    
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

    @Query("SELECT AVG(i.executionTimeMs) FROM Idea i WHERE i.user.id = :userId")
    Double getAverageExecutionTimeForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(i) FROM User u JOIN u.favoriteIdeas i WHERE u.id = :userId")
    long countFavoriteIdeasByUserId(@Param("userId") Long userId);

    @Query("SELECT i FROM Idea i JOIN i.favoritedByUsers u WHERE u.id = :userId")
    Page<Idea> findFavoriteIdeasByUserId(@Param("userId") Long userId, Pageable pageable);
}