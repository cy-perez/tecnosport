package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el primer {@code ADMIN} si no existe ninguno con ese correo (docs/09-plan-de-arranque.md:
 * "lo mínimo" antes de contraentrega). Nunca actualiza uno que ya existe — rotar la clave de un
 * admin ya creado es un mecanismo aparte, no construido todavía.
 */
@Component
public class SembradorAdmin implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SembradorAdmin.class);

  private final RepositorioUsuarios repositorioUsuarios;
  private final CodificadorDeClaves codificadorDeClaves;
  private final Reloj reloj;
  private final PropiedadesAdminSemilla propiedades;

  public SembradorAdmin(
      RepositorioUsuarios repositorioUsuarios,
      CodificadorDeClaves codificadorDeClaves,
      Reloj reloj,
      PropiedadesAdminSemilla propiedades) {
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.reloj = Objects.requireNonNull(reloj);
    this.propiedades = Objects.requireNonNull(propiedades);
  }

  @Override
  public void run(ApplicationArguments args) {
    CorreoElectronico correo = new CorreoElectronico(propiedades.correo());
    if (repositorioUsuarios.buscarPorCorreo(correo).isPresent()) {
      return;
    }
    Instant ahora = reloj.ahora();
    Usuario admin =
        Usuario.crear(correo, codificadorDeClaves.codificar(propiedades.clave()), Rol.ADMIN, ahora);
    // El ADMIN sembrado nunca pasa por registro ni por el enlace de verificación — nace
    // verificado.
    admin.verificarCorreo(ahora);
    repositorioUsuarios.guardar(admin);
    log.info("Usuario ADMIN inicial creado: {}", correo.valor());
  }
}
