package co.tecnosport.api.domain.pedido;

/** Tabla de métodos de docs/11-pagos-y-envios.md. */
public enum MetodoPago {
  TARJETA,
  PSE,
  NEQUI,
  BANCOLOMBIA,
  ADDI,
  TRANSFERENCIA_MANUAL,
  CONTRAENTREGA;

  /**
   * Los métodos que resuelve la pasarela de pagos y no el negocio (docs/11-pagos-y-envios.md).
   * Vivía como un {@code switch} privado en {@code CrearIntentoDePago}; subió aquí cuando un
   * segundo sitio necesitó la misma pregunta —qué métodos puede apagar la configuración de la
   * pasarela— y dos {@code switch} sobre el mismo enum en capas distintas es una invitación a que
   * se separen.
   *
   * <p>Sin {@code default} a propósito: un método nuevo en este enum no compila hasta que alguien
   * decida de qué lado cae.
   */
  public boolean seProcesaPorPasarela() {
    return switch (this) {
      case TARJETA, PSE, NEQUI, BANCOLOMBIA, ADDI -> true;
      case TRANSFERENCIA_MANUAL, CONTRAENTREGA -> false;
    };
  }
}
