package projeto_gerador_ideias_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import projeto_gerador_ideias_backend.model.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUuid(UUID uuid);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.generatedIdeasCount = u.generatedIdeasCount + 1 WHERE u.email = :email")
    int incrementGeneratedIdeasCount(String email);
}
