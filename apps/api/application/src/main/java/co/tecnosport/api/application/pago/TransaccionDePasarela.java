package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Lo que la pasarela responde al consultar una transacción por su id: su estado, con qué medio se
 * cobró de verdad ({@code payment_method_type}), y —lo que la ata a un pago nuestro— la referencia
 * que le dimos al crearla y el monto que de verdad se movió.
 *
 * <p>Antes la consulta devolvía solo el estado, y por eso la conciliación programada resolvía el
 * pedido sin enterarse nunca de que el comprador había pagado con algo distinto de lo que eligió.
 * {@code medio} puede ser nulo: una respuesta sin ese campo no invalida el estado.
 *
 * <p><strong>{@code referencia} y {@code monto} no pueden ser nulos, y esa es la
 * corrección.</strong> El id de transacción con el que la conciliación consulta lo estampa {@code
 * PATCH /pagos/intentos/{referencia}}, que es público y anónimo porque el Web Checkout devuelve el
 * id en la URL de retorno del navegador. Mientras este objeto llevara solo el estado, la
 * conciliación no tenía con qué comprobar que la transacción consultada fuera la de este pago:
 * cualquiera podía estampar sobre su pedido nuevo el id de una transacción aprobada ajena —la suya
 * de COP 20.000, por ejemplo— y a los quince minutos la tarea le daba el pedido por pagado. Un
 * objeto que no puede llevar la contraprueba obliga a que quien lo use se fíe; por eso la
 * contraprueba es obligatoria aquí y no un campo opcional que alguien pueda olvidarse de mirar.
 */
public record TransaccionDePasarela(String estado, String medio, String referencia, Dinero monto) {

  public TransaccionDePasarela {
    if (estado == null || estado.isBlank()) {
      throw new IllegalArgumentException("El estado de la transacción no puede estar vacío.");
    }
    if (referencia == null || referencia.isBlank()) {
      throw new IllegalArgumentException("La referencia de la transacción no puede estar vacía.");
    }
    if (monto == null) {
      throw new IllegalArgumentException("El monto de la transacción no puede ser nulo.");
    }
  }

  /**
   * Si esta transacción es la del pago que se está conciliando: la referencia es la nuestra y el
   * monto movido no se queda corto.
   *
   * <p>Se acepta que la pasarela reporte <em>más</em> de lo que pedimos —eso no es una pérdida y no
   * se ha visto nunca— y se rechaza que reporte menos, que es el caso del cupo tope de un
   * prestamista y el de la transacción ajena de otro importe.
   */
  public boolean correspondeA(String referenciaDelPago, Dinero montoDelPago) {
    return referencia.equals(referenciaDelPago)
        && monto.valor().compareTo(montoDelPago.valor()) >= 0;
  }
}
