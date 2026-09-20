package co.tecnosport.api.application.pago;

/**
 * La transacción se creó pero no hay a dónde mandar al comprador. Dos causas, y la excepción las
 * distingue porque al comprador se le cuentan distinto:
 *
 * <ul>
 *   <li>El medio de pago la rechazó —{@code Rejected} con un código: {@code 801} si esa persona ya
 *       tiene una solicitud en curso, {@code 802} si el monto no llega al mínimo—. Eso se puede
 *       explicar, y hay que explicarlo: un mensaje genérico deja al comprador sin saber si
 *       reintentar.
 *   <li>La pasarela se quedó pensando más de lo que dura el sondeo. Ahí no hay nada que explicar y
 *       lo razonable es reintentar o elegir otro medio.
 * </ul>
 *
 * <p>El {@code Pago} ya quedó guardado con su id cuando esto se lanza: la transacción existe del
 * lado de Sistecrédito y la conciliación tiene que poder encontrarla.
 */
public class SistecreditoNoEntregoLaUrlDePagoException extends RuntimeException {

  private final String estado;
  private final String codigo;
  private final String descripcion;

  public SistecreditoNoEntregoLaUrlDePagoException(
      String estado, String codigo, String descripcion) {
    super(
        "Sistecrédito no entregó la URL de pago (estado="
            + estado
            + ", código="
            + codigo
            + "): "
            + descripcion);
    this.estado = estado;
    this.codigo = codigo;
    this.descripcion = descripcion;
  }

  public String estado() {
    return estado;
  }

  /** El código del medio de pago ({@code 800}, {@code 801}, {@code 802}), o nulo si no lo dio. */
  public String codigo() {
    return codigo;
  }

  public String descripcion() {
    return descripcion;
  }
}
