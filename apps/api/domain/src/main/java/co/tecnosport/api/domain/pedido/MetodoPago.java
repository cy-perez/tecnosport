package co.tecnosport.api.domain.pedido;

/**
 * Tabla de métodos de docs/11-pagos-y-envios.md.
 *
 * <p><b>{@code WOMPI} es uno y fueron cuatro.</b> Hasta el 28 de septiembre de 2026 el enum tenía
 * {@code TARJETA}, {@code PSE}, {@code NEQUI} y {@code BANCOLOMBIA}, y el checkout los ofrecía como
 * cuatro botones. Los cuatro los cobraba Wompi, y ahí estaba el problema: <b>lo que el comprador
 * elegía aquí nunca viajaba</b>. La URL del Web Checkout hospedado no le manda a Wompi el método
 * elegido —Wompi pinta su propia lista y el comprador vuelve a elegir allí— así que un pedido podía
 * decir {@code NEQUI} y haberse cobrado con tarjeta, sin ninguna señal. Está escrito desde la
 * {@code V39}, que añadió {@code pago.medio_reportado_pasarela} justamente para tener la evidencia
 * de lo que sí pasó.
 *
 * <p>Un valor que el sistema no puede honrar no es un dato, es una intención disfrazada de dato.
 * Agrupar los cuatro en {@code WOMPI} dice la verdad: <b>"esto lo cobra Wompi, y cuál de sus medios
 * lo decide el comprador en su pantalla"</b>. Con qué se cobró de verdad sigue en {@code
 * medio_reportado_pasarela}, crudo y sin traducir.
 *
 * <p><b>{@code NEQUI} no entra en el grupo</b>, y no por descuido: el 28 de septiembre de 2026 se
 * decidió sacarlo de Wompi y recibirlo como transferencia manual. Deja de confirmarse solo, así que
 * un pedido con Nequi se queda en {@code PAGO_PENDIENTE} hasta que alguien concilie el comprobante.
 * Los pedidos históricos con {@code NEQUI} sí migran a {@code WOMPI} ({@code V62}): los cobró la
 * pasarela, y decir que se conciliaron a mano sería falsear el historial.
 *
 * <p>Precedente cercano y distinto: {@code ADDI} salió del enum en la {@code V61} sin migrar una
 * sola fila, porque nunca se pudo elegir. Estos cuatro sí se pudieron, y por eso la {@code V62}
 * mueve datos en vez de solo comprobar.
 */
public enum MetodoPago {
  /**
   * Los medios que cobra Wompi: tarjeta, PSE y el botón de Bancolombia. Cuál, lo elige el comprador
   * en la pantalla de Wompi, no aquí.
   */
  WOMPI,
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
   * ProveedorDePago.WOMPI}, donde Addi no existe, y eso era lo único que lo mantenía fuera del
   * checkout: la lista de habilitados de la cuenta no lo incluye y el filtro lo quitaba por eso. Un
   * valor que solo se sostenía por un efecto lateral de otra configuración. Vuelve cuando se
   * integre de verdad —el sitio tiene que estar en producción para que Addi estudie la activación—,
   * y volverá con su propio {@code ProveedorDePago}.
   *
   * <p>Sin {@code default} a propósito: un método nuevo en este enum no compila hasta que alguien
   * decida quién lo cobra.
   */
  public ProveedorDePago pasarela() {
    return switch (this) {
      case WOMPI -> ProveedorDePago.WOMPI;
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
