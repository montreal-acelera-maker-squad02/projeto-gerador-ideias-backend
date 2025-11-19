package projeto_gerador_ideias_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "themes")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Theme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public Theme(String name) {
        this.name = name;
    }
}
