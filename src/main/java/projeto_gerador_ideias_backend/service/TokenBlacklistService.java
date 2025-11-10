package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_CACHE_NAME = "tokenBlacklist";

    private final CacheManager cacheManager;

    private Cache getCache() {
        Cache cache = cacheManager.getCache(BLACKLIST_CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache '" + BLACKLIST_CACHE_NAME + "' não encontrado. Verifique a propriedade 'spring.cache.cache-names' em application.properties.");
        }
        return cache;
    }

    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        getCache().put(token, true);
        log.debug("Token adicionado à blacklist");
    }

    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Cache cache = getCache();
        Cache.ValueWrapper wrapper = cache.get(token);
        return wrapper != null && wrapper.get() != null;
    }

    public void removeFromBlacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        getCache().evict(token);
        log.debug("Token removido da blacklist");
    }
}

