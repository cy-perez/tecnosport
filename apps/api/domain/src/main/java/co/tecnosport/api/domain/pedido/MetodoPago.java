package co.tecnosport.api.domain.pedido;

/** Tabla de métodos de docs/11-pagos-y-envios.md. */
public enum MetodoPago {
  TARJETA,
  PSE,
  NEQUI,
  BANCOLOMBIA,
  SISTECREDITO,
  TRANSFERENCIA_MANUAL,
  CONTRAENTREGA;

  /**
   * Qué proveedor resuelve el cobro de este método (docs/11-pagos-y-envios.md).
   *
   * <p>Devolvía un booleano, {@code seProcesaPorPasarela()}, hasta que entró Sistecrédito ({@code
   * adr/0048}): ese predicado decía "pasarela" y significaba "Wompi", así que con dos proveedores
   * dejaba de alcanzar. Vivía antes como un {@code switch} privado en {@code CrearIntentoDePago} y
   * subió aquí cuando un segundo sitio necesitó la misma pregunta.
   *
   * <p><b>{@code ADDI} estuvo aquí y se fue</b> (22 de septiembre de 2026, V61). Apuntaba a {@code
   * WOMPI}, donde Addi no existe, y eso era lo único que lo mantenía fuera del checkout: la lista
   * de habilitados de la cuenta no lo incluye y el filtro lo quitaba por eso. Un valor que solo se
   * sostenía por un efecto lateral de otra configuración. Vuelve cuando se integre de verdad —el
   * sitio tiene que estar en producción para que Addi estudie la activación—, y volverá con su
   * propio {@code ProveedorDePago}.
   *
   * <p>Sin {@code default} a propósito: un método nuevo en este enum no compila hasta que alguien
   * decida quién lo cobra.
   */
  public ProveedorDePago pasarela() {
    return switch (this) {
      case TARJETA, PSE, NEQUI, BANCOLOMBIA -> ProveedorDePago.WOMPI;
      case SISTECREDITO -> ProveedorDePago.SISTECREDITO;
      case TRANSFERENCIA_MANUAL, CONTRAENTREGA -> ProveedorDePago.NINGUNO;
    };
  }

  /**
   * Atajo para "lo cobra un tercero por su cuenta y no el negocio a mano". Es la forma que tenían
   * los dos sitios que preguntaban antes de que existieran dos proveedores, y sigue siendo lo que
   * necesitan: uno decide si hay intento de pago que pedir, el otro si la configuración puede
   * apagarlo.
   */
  public boolean laCobraUnaPasarela() {
    return pasarela() != ProveedorDePago.NINGUNO;
  }
}
