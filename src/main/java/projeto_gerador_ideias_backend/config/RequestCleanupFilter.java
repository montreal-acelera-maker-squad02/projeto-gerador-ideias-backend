package projeto_gerador_ideias_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import projeto_gerador_ideias_backend.service.UserCacheService;

import java.io.IOException;

@Component
@ConditionalOnBean(UserCacheService.class)
@RequiredArgsConstructor
public class RequestCleanupFilter extends OncePerRequestFilter {

    private final UserCacheService userCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            userCacheService.clearRequestCache();
        }
    }
}



