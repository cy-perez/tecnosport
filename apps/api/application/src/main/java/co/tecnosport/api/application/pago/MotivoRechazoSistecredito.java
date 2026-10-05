package co.tecnosport.api.application.pago;

/**
 * Por qué Sistecrédito no entregó a dónde mandar al comprador, ya clasificado. Lo que el comprador
 * tiene que hacer depende de esto, y por eso se decide aquí, una vez, y no en cada cliente.
 *
 * <p>La clasificación vivía en un componente Angular, con el vocabulario crudo de la pasarela
 * ({@code '801'}, {@code 'Rejected'}), en tres tablas que no coincidían: el cliente HTTP, {@link
 * EstadosSistecredito} y la pantalla distinguían mayúsculas distinto y no trataban igual {@code
 * Failed}. La app móvil habría sido la cuarta.
 */
public enum MotivoRechazoSistecredito {
  /** {@code 801}: esa persona ya tiene una solicitud de crédito en curso. */
  SOLICITUD_EN_CURSO,
  /** {@code 802}: el monto no llega al mínimo del crédito. */
  MONTO_INSUFICIENTE,
  /** La pasarela negó el crédito; reintentar no lo cambia. */
  CREDITO_NEGADO,
  /**
   * La pasarela no decidió a tiempo, o falló de su lado: se puede reintentar u elegir otro medio.
   */
  SIN_RESPUESTA;

  static MotivoRechazoSistecredito de(String codigo, String estado) {
    if ("801".equals(codigo)) {
      return SOLICITUD_EN_CURSO;
    }
    if ("802".equals(codigo)) {
      return MONTO_INSUFICIENTE;
    }
    return EstadosSistecredito.aEstadoPago(estado)
            == co.tecnosport.api.domain.pago.EstadoPago.RECHAZADO
        ? CREDITO_NEGADO
        : SIN_RESPUESTA;
  }
}
