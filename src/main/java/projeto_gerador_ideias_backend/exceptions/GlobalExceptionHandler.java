package projeto_gerador_ideias_backend.exceptions;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse(
                "Cadastro não realizado",
                "O email fornecido não pode ser usado para cadastro. Use outro email ou tente recuperar sua conta."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        ErrorResponse error = new ErrorResponse("Erro de validação", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        StringBuilder messages = new StringBuilder();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String errorMessage = error.getDefaultMessage();
            if (!messages.isEmpty()) {
                messages.append("; ");
            }
            messages.append(errorMessage);
        });
        ErrorResponse error = new ErrorResponse("Erro de validação", messages.toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseException(HttpMessageNotReadableException ex) {
        String message = "Erro ao processar o JSON. Verifique o formato enviado.";
        if (ex.getCause() != null && ex.getCause().getMessage().contains("Categoria inválida")) {
            message = "A categoria fornecida não é válida. Valores aceitos: tecnologia, saude, financas, culinaria, marketing, diaria.";
        }
        ErrorResponse error = new ErrorResponse("Requisição Inválida", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();

        Class<?> type = ex.getRequiredType();
        String requiredType = (type != null) ? type.getSimpleName() : "desconhecido";

        Object val = ex.getValue();
        String value = (val != null) ? val.toString() : "null";

        String message = String.format("O parâmetro '%s' ('%s') não pôde ser convertido para o tipo '%s'.", name, value, requiredType);

        ErrorResponse error = new ErrorResponse("Requisição Inválida", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("Recurso não encontrado", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWrongPassword(WrongPasswordException ex) {
        ErrorResponse error = new ErrorResponse("Erro de autenticação", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(TokenLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTokenLimitExceeded(TokenLimitExceededException ex) {
        ErrorResponse error = new ErrorResponse("Limite de tokens atingido", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ChatPermissionException.class)
    public ResponseEntity<ErrorResponse> handleChatPermission(ChatPermissionException ex) {
        ErrorResponse error = new ErrorResponse("Acesso negado", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(OllamaServiceException.class)
    public ResponseEntity<ErrorResponse> handleOllamaServiceException(OllamaServiceException ex) {
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (message != null) {
            String lowerMessage = message.toLowerCase();

            if (lowerMessage.contains("timeout") || 
                lowerMessage.contains("conexão recusada") ||
                lowerMessage.contains("connection refused") ||
                lowerMessage.contains("não foi possível conectar") ||
                lowerMessage.contains("verifique se o ollama está rodando") ||
                lowerMessage.contains("ollama não está rodando") ||
                lowerMessage.contains("para iniciar: ollama serve") ||
                (lowerMessage.contains("erro http 4") && !lowerMessage.contains("falha na moderação"))) {
                status = HttpStatus.BAD_REQUEST;
            }

            if (lowerMessage.contains("falha na moderação") ||
                (lowerMessage.contains("erro http 5") && !lowerMessage.contains("verifique se o ollama está rodando")) ||
                lowerMessage.contains("internal server error")) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        
        ErrorResponse error = new ErrorResponse("Erro ao comunicar com a IA", ex.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockException ex) {
        ErrorResponse error = new ErrorResponse("Conflito de versão", "A sessão foi atualizada por outra requisição. Tente novamente.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler({InvalidDataAccessApiUsageException.class, IllegalTransactionStateException.class})
    public ResponseEntity<ErrorResponse> handleTransactionException(Exception ex) {
        ErrorResponse error = new ErrorResponse("Erro de transação", "Erro ao processar operação no banco de dados. Tente novamente.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("Argumento Inválido", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGenericRuntimeException(RuntimeException ex) {
        ErrorResponse error = new ErrorResponse("Erro interno do servidor", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse("Erro interno do servidor", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    public static class ErrorResponse {
        private String error;
        private String message;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}