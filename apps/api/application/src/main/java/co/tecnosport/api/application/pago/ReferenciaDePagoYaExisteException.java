package co.tecnosport.api.application.pago;

/**
 * Ya había un pago con esa referencia: otra petición de intento ganó la carrera.
 *
 * <p>El número de intento sale de contar los pagos del pedido ({@code buscarPorPedidoId(...).size()
 * + 1}), así que dos peticiones simultáneas de intento sobre el mismo pedido calculan el mismo y
 * construyen la misma {@link co.tecnosport.api.domain.pago.ReferenciaPago}. La segunda choca contra
 * el {@code unique} de {@code pago.referencia}, que es lo que de verdad protege los datos; esto
 * solo le pone nombre al choque.
 *
 * <p><b>Existe porque ese choque salía disfrazado.</b> {@code RepositorioPagosJpa.guardar} volcaba
 * la sesión entera al guardar los eventos —{@code saveAllAndFlush} arrastra el {@code insert} del
 * pago— y su {@code catch} traducía <em>cualquier</em> violación de integridad a {@link
 * EventoDePagoYaRegistradoException}. Con eso, una referencia repetida se reportaba como "el evento
 * X ya estaba registrado", con un id de evento "desconocido", y acababa en un 500 que mandaba a
 * buscar un evento duplicado que no existía. Peor: por el camino del webhook, {@code
 * PagoControlador} atrapa esa excepción y responde <b>200 "ya procesado"</b>, o sea que una
 * escritura que falló por otro motivo se le habría dado por buena a la pasarela.
 */
public class ReferenciaDePagoYaExisteException extends RuntimeException {

  private final String referencia;

  public ReferenciaDePagoYaExisteException(String referencia, Throwable causa) {
    super("Ya existe un pago con la referencia " + referencia + ".", causa);
    this.referencia = referencia;
  }

  public String referencia() {
    return referencia;
  }
}
