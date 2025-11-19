package projeto_gerador_ideias_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import projeto_gerador_ideias_backend.model.UserFavorite;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UserFavorite.UserFavoriteId> {

    @Modifying
    @Query(value = "INSERT INTO user_favorites (user_id, idea_id) VALUES (:userId, :ideaId)", nativeQuery = true)
    void addFavorite(@Param("userId") Long userId, @Param("ideaId") Long ideaId);

    @Query("SELECT COUNT(uf) > 0 FROM UserFavorite uf WHERE uf.userId = :userId AND uf.ideaId = :ideaId")
    boolean existsByUserIdAndIdeaId(@Param("userId") Long userId, @Param("ideaId") Long ideaId);
}