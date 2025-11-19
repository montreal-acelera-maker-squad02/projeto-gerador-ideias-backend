package projeto_gerador_ideias_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    
    /**
     * @deprecated Use {@link #getAccessToken()} instead.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    @JsonIgnore
    public String getToken() {
        return accessToken;
    }
    
    /**
     * @deprecated Use {@link #setAccessToken(String)} instead.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public void setToken(String token) {
        this.accessToken = token;
    }
}



