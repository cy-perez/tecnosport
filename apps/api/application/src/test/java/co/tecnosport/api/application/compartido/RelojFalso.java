package co.tecnosport.api.application.compartido;

import java.time.Instant;

/** Instante fijo, para probar sin depender del reloj del sistema. */
public final class RelojFalso implements Reloj {

  private final Instant fijo;

  public RelojFalso(Instant fijo) {
    this.fijo = fijo;
  }

  @Override
  public Instant ahora() {
    return fijo;
  }
}
