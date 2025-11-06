package projeto_gerador_ideias_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.repository.UserRepository;

@Service
public class UserStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(UserStatisticsService.class);

    private final UserRepository userRepository;

    public UserStatisticsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void incrementUserIdeaCount(String email) {
        int updatedRows = userRepository.incrementGeneratedIdeasCount(email);
        if (updatedRows == 0) {
            log.warn("Tentativa de incrementar contador para usuário não encontrado ou inativo: {}", email);
        }
    }
}