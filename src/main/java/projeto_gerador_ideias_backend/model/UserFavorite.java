package projeto_gerador_ideias_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "user_favorites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserFavorite.UserFavoriteId.class)
public class UserFavorite {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "idea_id")
    private Long ideaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idea_id", insertable = false, updatable = false)
    private Idea idea;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserFavoriteId implements Serializable {
        private Long userId;
        private Long ideaId;
    }
}