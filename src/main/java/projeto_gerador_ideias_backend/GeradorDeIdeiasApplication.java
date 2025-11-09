package projeto_gerador_ideias_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableRetry
@EnableCaching
@EnableAsync
public class GeradorDeIdeiasApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeradorDeIdeiasApplication.class, args);
	}

}
