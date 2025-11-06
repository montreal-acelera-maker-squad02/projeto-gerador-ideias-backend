package projeto_gerador_ideias_backend.exceptions;

public class OllamaServiceException extends RuntimeException {
    
    public OllamaServiceException(String message) {
        super(message);
    }
    
    public OllamaServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

