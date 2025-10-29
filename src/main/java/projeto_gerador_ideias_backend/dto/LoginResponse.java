package projeto_gerador_ideias_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private UUID uuid;
    private String name;
    private String email;
    private String token;
}


