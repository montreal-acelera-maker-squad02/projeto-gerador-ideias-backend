package projeto_gerador_ideias_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GeradorDeIdeiasApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeradorDeIdeiasApplication.class, args);
	}

}
