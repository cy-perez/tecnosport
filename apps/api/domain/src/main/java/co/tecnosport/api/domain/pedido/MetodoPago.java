package co.tecnosport.api.domain.pedido;

/** Tabla de métodos de docs/11-pagos-y-envios.md. */
public enum MetodoPago {
  TARJETA,
  PSE,
  NEQUI,
  BANCOLOMBIA,
  ADDI,
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
   * <p><b>{@code ADDI} sigue apuntando a {@code WOMPI} y eso es a propósito</b>, aunque Wompi no lo
   * ofrezca: es lo que mantiene el método fuera del checkout, porque la lista de habilitados de
   * Wompi no lo incluye y el filtro de {@code MetodosDePagoDisponibles} lo quita por eso.
   * Reclasificarlo a {@code NINGUNO} —que suena más honesto— lo dejaría <b>sin filtro y ofrecido
   * siempre</b>. El {@code TODO} de docs/11-pagos-y-envios.md sobre qué hacer con este valor sigue
   * abierto y es el sitio donde se resuelve de verdad.
   *
   * <p>Sin {@code default} a propósito: un método nuevo en este enum no compila hasta que alguien
   * decida quién lo cobra.
   */
  public ProveedorDePago pasarela() {
    return switch (this) {
      case TARJETA, PSE, NEQUI, BANCOLOMBIA, ADDI -> ProveedorDePago.WOMPI;
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
