package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import java.time.Instant;

/** Instante fijo, para probar sin depender del reloj del sistema. */
final class RelojFalso implements Reloj {

  private final Instant fijo;

  RelojFalso(Instant fijo) {
    this.fijo = fijo;
  }

  @Override
  public Instant ahora() {
    return fijo;
  }
}
