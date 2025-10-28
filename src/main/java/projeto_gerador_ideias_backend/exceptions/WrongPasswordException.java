package projeto_gerador_ideias_backend.exceptions;

public class WrongPasswordException extends RuntimeException {
    
    public WrongPasswordException(String message) {
        super(message);
    }
    
    public WrongPasswordException(String message, Throwable cause) {
        super(message, cause);
    }
}
