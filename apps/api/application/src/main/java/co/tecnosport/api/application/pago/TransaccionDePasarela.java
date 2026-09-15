package co.tecnosport.api.application.pago;

/**
 * Lo que la pasarela responde al consultar una transacción por su id: su estado y con qué medio se
 * cobró de verdad ({@code payment_method_type}).
 *
 * <p>Antes la consulta devolvía solo el estado, y por eso la conciliación programada resolvía el
 * pedido sin enterarse nunca de que el comprador había pagado con algo distinto de lo que eligió.
 * {@code medio} puede ser nulo: una respuesta sin ese campo no invalida el estado, que es lo que la
 * conciliación necesita para cerrar el pago.
 */
public record TransaccionDePasarela(String estado, String medio) {

  public TransaccionDePasarela {
    if (estado == null || estado.isBlank()) {
      throw new IllegalArgumentException("El estado de la transacción no puede estar vacío.");
    }
  }
}
