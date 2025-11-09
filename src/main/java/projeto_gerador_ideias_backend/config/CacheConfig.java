package projeto_gerador_ideias_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import projeto_gerador_ideias_backend.model.User;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    
    @Bean
    public Cache<String, User> userEmailCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}








