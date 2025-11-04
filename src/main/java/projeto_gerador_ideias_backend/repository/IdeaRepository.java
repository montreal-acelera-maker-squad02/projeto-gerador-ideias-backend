package projeto_gerador_ideias_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Long> {
    Optional<Idea> findByTheme(String theme);
    List<Idea> findAllByOrderByCreatedAtDesc();

    List<Idea> findByThemeOrderByCreatedAtDesc(Theme theme);

    List<Idea> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<Idea> findByThemeAndCreatedAtBetweenOrderByCreatedAtDesc(Theme theme, LocalDateTime start, LocalDateTime end);

    List<Idea> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Idea> findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(User user, Theme theme, String context);
}