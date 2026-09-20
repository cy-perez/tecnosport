package co.tecnosport.api.application.pago;

/**
 * Qué pasó con una notificación de Sistecrédito. Enum propio y no el de Wompi ({@link
 * ResultadoEventoDePago}) porque los dos primeros valores no existen allá y {@code FIRMA_INVALIDA}
 * no existe aquí: Sistecrédito no firma sus notificaciones, y lo que las autentica es el contraste
 * contra la consulta ({@code adr/0048}).
 */
public enum ResultadoNotificacionSistecredito {
  APLICADO,

  /** Ver {@link ResultadoEventoDePago#APLICADO_SIN_CONFIRMAR_INVENTARIO}. */
  APLICADO_SIN_CONFIRMAR_INVENTARIO,

  YA_PROCESADO,

  /**
   * No se pudo preguntarle a la pasarela si la notificación es cierta. <b>No se aplica nada</b>:
   * sin verificación, aplicar sería creerle a un cuerpo JSON que cualquiera puede enviar a un
   * endpoint público. La conciliación programada recoge el pago después.
   */
  NO_SE_PUDO_VERIFICAR,

  /**
   * La pasarela dice algo distinto de lo que decía la notificación. O alguien intentó falsificarla,
   * o la transacción cambió entre una cosa y la otra; en los dos casos manda la consulta, y aquí no
   * se aplica nada.
   */
  DISCREPANCIA_CON_LA_PASARELA,

  PAGO_NO_ENCONTRADO,
  ESTADO_NO_SOPORTADO
}
