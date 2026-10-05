package co.tecnosport.api.application.pago;

/**
 * El id que llegó por {@code PATCH /pagos/intentos/{referencia}} no es de una transacción de este
 * pago, o la pasarela no supo decir de quién es. No se registra: el primero que se registra es el
 * que se queda, y uno ajeno dejaba el pago sin poder conciliarse nunca.
 */
public class TransaccionDeOtroPagoException extends RuntimeException {

  public TransaccionDeOtroPagoException(String referencia) {
    super(
        "La transacción indicada no corresponde al pago "
            + referencia
            + ", o la pasarela no pudo confirmarlo.");
  }
}
