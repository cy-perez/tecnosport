package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Borra las respuestas de idempotencia vencidas (24 horas) y las reclamaciones abandonadas. Guardan
 * el cuerpo de la respuesta de crear un pedido —nombre, teléfono, dirección, correo—, y sin esto no
 * se borraban nunca. El repositorio abre su propia transacción: no hace falta otra aquí.
 */
@Component
public class TareaPurgaIdempotencia {

  private static final Logger log = LoggerFactory.getLogger(TareaPurgaIdempotencia.class);

  private final RepositorioIdempotencia repositorio;
  private final Reloj reloj;

  public TareaPurgaIdempotencia(RepositorioIdempotencia repositorio, Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void purgar() {
    int borradas = repositorio.purgarVencidas(reloj.ahora());
    if (borradas > 0) {
      log.info("Purga de idempotencia: {} llaves vencidas o abandonadas borradas", borradas);
    }
  }
}
