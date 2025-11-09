package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class FailureCounterService {

    private static final String FAILURE_CACHE_NAME = "consecutiveFailureCache";
    private static final int FAILURE_THRESHOLD = 4;

    private final Cache failureCache;
    private final EmailService emailService;

    public FailureCounterService(CacheManager cacheManager, EmailService emailService) {
        Cache cache = cacheManager.getCache(FAILURE_CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache '" + FAILURE_CACHE_NAME + "' não encontrado. Verifique a propriedade 'spring.cache.cache-names' em application.properties.");
        }
        this.failureCache = cache;
        this.emailService = emailService;
    }

    public synchronized void handleFailure(String userEmail, String userName) {
        Cache.ValueWrapper valueWrapper = failureCache.get(userEmail);
        int currentFailures = (valueWrapper != null) ? (Integer) valueWrapper.get() : 0;
        currentFailures++;

        failureCache.put(userEmail, currentFailures);

        if (currentFailures == FAILURE_THRESHOLD) {
            emailService.sendSystemErrorNotification(userEmail, userName, currentFailures);
            resetCounter(userEmail);
        }
    }

    public void resetCounter(String userEmail) {
        failureCache.evict(userEmail);
    }
}