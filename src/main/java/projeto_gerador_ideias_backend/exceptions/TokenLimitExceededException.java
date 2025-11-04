package projeto_gerador_ideias_backend.exceptions;

public class TokenLimitExceededException extends RuntimeException {
    public TokenLimitExceededException(String message) {
        super(message);
    }
}

