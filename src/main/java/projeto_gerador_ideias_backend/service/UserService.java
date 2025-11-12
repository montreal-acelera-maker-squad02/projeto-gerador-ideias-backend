package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.request.LoginRequest;
import projeto_gerador_ideias_backend.dto.response.LoginResponse;
import projeto_gerador_ideias_backend.dto.request.RegisterRequest;
import projeto_gerador_ideias_backend.dto.response.RegisterResponse;
import projeto_gerador_ideias_backend.dto.request.RefreshTokenRequest;
import projeto_gerador_ideias_backend.dto.response.RefreshTokenResponse;
import projeto_gerador_ideias_backend.dto.request.UpdateUserRequest;
import projeto_gerador_ideias_backend.model.RefreshToken;
import projeto_gerador_ideias_backend.repository.RefreshTokenRepository;
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
    private final JwtService jwtService;
    private final UserCacheService userCacheService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final IdeasSummaryCacheService ideasSummaryCacheService;
    
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
        
        String accessToken = jwtService.generateAccessToken(savedUser.getEmail(), savedUser.getId());
        String refreshTokenString = jwtService.generateRefreshToken(savedUser.getEmail(), savedUser.getId());
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(savedUser);
        refreshToken.setToken(refreshTokenString);
        refreshToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        
        RegisterResponse response = new RegisterResponse();
        response.setId(savedUser.getId());
        response.setUuid(savedUser.getUuid());
        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setCreatedAt(savedUser.getCreatedAt());
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshTokenString);
        
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
        
        userCacheService.invalidateUserCache(updatedUser.getEmail());
        
        RegisterResponse response = new RegisterResponse();
        response.setId(updatedUser.getId());
        response.setUuid(updatedUser.getUuid());
        response.setName(updatedUser.getName());
        response.setEmail(updatedUser.getEmail());
        response.setCreatedAt(updatedUser.getCreatedAt());
        
        return response;
    }
    
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Credenciais inválidas"));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new WrongPasswordException("Credenciais inválidas");
        }
        
        if (user.getEnabled() == null || !user.getEnabled()) {
            throw new ValidationException("Conta desativada. Entre em contato com o suporte.");
        }
        
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());
        String refreshTokenString = jwtService.generateRefreshToken(user.getEmail(), user.getId());
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(refreshTokenString);
        refreshToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        
        LoginResponse response = new LoginResponse();
        response.setUuid(user.getUuid());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshTokenString);
        
        ideasSummaryCacheService.preloadUserIdeasSummary(user.getId());
        
        return response;
    }
    
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtService.validateRefreshToken(request.getRefreshToken())) {
            throw new ValidationException("Refresh token inválido ou expirado");
        }
        
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new ValidationException("Refresh token não encontrado ou revogado"));
        
        if (refreshToken.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new ValidationException("Refresh token expirado");
        }
        
        User user = refreshToken.getUser();
        if (user.getEnabled() == null || !user.getEnabled()) {
            throw new ValidationException("Conta desativada. Entre em contato com o suporte.");
        }
        
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());
        String newRefreshTokenString = jwtService.generateRefreshToken(user.getEmail(), user.getId());
        
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setToken(newRefreshTokenString);
        newRefreshToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        newRefreshToken.setRevoked(false);
        refreshTokenRepository.save(newRefreshToken);
        
        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshTokenString);
        
        return response;
    }
    
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklistToken(accessToken);
        }
        
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(rt -> {
                        rt.setRevoked(true);
                        refreshTokenRepository.save(rt);
                    });
        }
    }
}

