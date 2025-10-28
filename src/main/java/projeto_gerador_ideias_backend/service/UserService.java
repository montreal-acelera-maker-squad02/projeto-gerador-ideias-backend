package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.RegisterRequest;
import projeto_gerador_ideias_backend.dto.RegisterResponse;
import projeto_gerador_ideias_backend.dto.UpdateUserRequest;
import projeto_gerador_ideias_backend.exceptions.EmailAlreadyExistsException;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.exceptions.ValidationException;
import projeto_gerador_ideias_backend.exceptions.WrongPasswordException;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.UserRepository;
import projeto_gerador_ideias_backend.util.PasswordValidator;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email já está em uso: " + request.getEmail());
        }
        
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ValidationException("As senhas não coincidem");
        }
        
        if (!PasswordValidator.isValid(request.getPassword())) {
            throw new ValidationException(PasswordValidator.getPasswordRequirements());
        }
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        User savedUser = userRepository.save(user);
        
        RegisterResponse response = new RegisterResponse();
        response.setId(savedUser.getId());
        response.setUuid(savedUser.getUuid());
        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setCreatedAt(savedUser.getCreatedAt());
        
        return response;
    }
    
    @Transactional
    public RegisterResponse updateUser(String uuid, UpdateUserRequest request) {
        User user = userRepository.findByUuid(java.util.UUID.fromString(uuid))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
                throw new ValidationException("A senha atual é obrigatória para alterar sua senha");
            }
            
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new WrongPasswordException("A senha atual fornecida está incorreta. Verifique e tente novamente.");
            }
            
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new ValidationException("As novas senhas não coincidem");
            }
            
            if (!PasswordValidator.isValid(request.getPassword())) {
                throw new ValidationException(PasswordValidator.getPasswordRequirements());
            }
            
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        User updatedUser = userRepository.save(user);
        
        RegisterResponse response = new RegisterResponse();
        response.setId(updatedUser.getId());
        response.setUuid(updatedUser.getUuid());
        response.setName(updatedUser.getName());
        response.setEmail(updatedUser.getEmail());
        response.setCreatedAt(updatedUser.getCreatedAt());
        
        return response;
    }
}
