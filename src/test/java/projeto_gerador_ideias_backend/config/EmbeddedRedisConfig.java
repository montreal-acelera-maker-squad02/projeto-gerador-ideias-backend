package projeto_gerador_ideias_backend.config;

import com.github.fppt.jedismock.RedisServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import java.io.IOException;

@TestConfiguration
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    public EmbeddedRedisConfig() throws IOException {
        // Usa uma porta disponível aleatoriamente para evitar conflitos durante os testes
        this.redisServer = RedisServer.newRedisServer();
    }

    @PostConstruct
    public void startRedis() throws IOException {
        redisServer.start();
    }

    @PreDestroy
    public void stopRedis() {
        redisServer.stop();
    }
}