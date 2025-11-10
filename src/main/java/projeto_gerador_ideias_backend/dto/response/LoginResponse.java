package projeto_gerador_ideias_backend.dto.response;

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
    private String accessToken;
    private String refreshToken;
    
    @Deprecated
    public String getToken() {
        return accessToken;
    }
    
    @Deprecated
    public void setToken(String token) {
        this.accessToken = token;
    }
}



