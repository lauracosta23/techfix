package br.com.senac.aula08;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Aula 08 - Avaliacao Final: "TechFix Inteligente".
 *
 *   - Pagina:      http://localhost:8080/
 *   - Swagger UI:  http://localhost:8080/swagger-ui.html
 *   - H2 console:  http://localhost:8080/h2-console (JDBC URL jdbc:h2:mem:techfix, user sa)
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Aula08Application {

    public static void main(String[] args) {
        SpringApplication.run(Aula08Application.class, args);
    }
}
