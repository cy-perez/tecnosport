package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.usuario.RepositorioDesafiosSegundoFactor;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Borra los desafíos del segundo factor que ya vencieron (ADR-0096). Cada inicio de sesión de un
 * ADMIN con el factor activo deja una fila, y nada más la borraba. El repositorio abre su propia
 * transacción: no hace falta otra aquí.
 */
@Component
public class TareaPurgaDesafiosSegundoFactor {

  private static final Logger log = LoggerFactory.getLogger(TareaPurgaDesafiosSegundoFactor.class);

  private final RepositorioDesafiosSegundoFactor repositorio;
  private final Reloj reloj;

  public TareaPurgaDesafiosSegundoFactor(
      RepositorioDesafiosSegundoFactor repositorio, Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void purgar() {
    int borrados = repositorio.purgarVencidos(reloj.ahora());
    if (borrados > 0) {
      log.info("Purga del segundo factor: {} desafíos vencidos borrados", borrados);
    }
  }
}
