package co.tecnosport.api.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling aquí, no en ConfiguracionWompi: es una capacidad transversal del backend
// (docs/02-modelo-datos.md ya anticipa otro candidato, el vencimiento del carrito), no algo propio
// de Wompi. Primer uso: TareaConciliacionWompi.
@SpringBootApplication(scanBasePackages = "co.tecnosport.api")
@EnableScheduling
public class TecnosportApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(TecnosportApiApplication.class, args);
  }
}
