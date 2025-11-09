package projeto_gerador_ideias_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class GeradorDeIdeiasApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeradorDeIdeiasApplication.class, args);
	}

}
