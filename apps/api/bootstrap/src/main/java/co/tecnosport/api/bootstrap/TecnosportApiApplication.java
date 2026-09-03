package co.tecnosport.api.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling aquí, no en ConfiguracionWompi: es una capacidad transversal del backend
// (docs/02-modelo-datos.md ya anticipa otro candidato, el vencimiento del carrito), no algo propio
// de Wompi. Primer uso: TareaConciliacionWompi.
//
// UserDetailsServiceAutoConfiguration excluida: sin ella, Boot crea un usuario en memoria con
// contraseña generada al azar en cada arranque (log "Using generated security password") que
// nunca se usa — la autenticación es propia, por JWT (FiltroAutenticacionJwt), no por el
// AuthenticationManager/UserDetailsService de Spring Security.
@SpringBootApplication(
    scanBasePackages = "co.tecnosport.api",
    exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class TecnosportApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(TecnosportApiApplication.class, args);
  }
}
