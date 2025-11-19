package projeto_gerador_ideias_backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.UserRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserRepository userRepository;
    @Qualifier("userEmailCache")
    private final Cache<String, User> emailCache;

    private static class RequestUserCache {
        private User currentUser;
    }

    private final ThreadLocal<RequestUserCache> requestCache = ThreadLocal.withInitial(RequestUserCache::new);

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public User getCurrentAuthenticatedUser() {
        RequestUserCache cache = requestCache.get();
        if (cache.currentUser != null) {
            return cache.currentUser;
        }

        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails)) {
            throw new projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException("Usuário não autenticado. Não é possível usar o chat.");
        }

        String userEmail = ((org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal()).getUsername();
        
        User cachedUser = emailCache.getIfPresent(userEmail);
        if (cachedUser != null) {
            cache.currentUser = cachedUser;
            return cachedUser;
        }
        
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException(
                "Usuário autenticado não encontrado no banco de dados: " + userEmail));
        
        emailCache.put(userEmail, user);
        cache.currentUser = user;
        return user;
    }

    public void clearRequestCache() {
        requestCache.remove();
    }

    public void invalidateUserCache(String email) {
        emailCache.invalidate(email);
    }

    public void invalidateAllCache() {
        emailCache.invalidateAll();
    }
}

