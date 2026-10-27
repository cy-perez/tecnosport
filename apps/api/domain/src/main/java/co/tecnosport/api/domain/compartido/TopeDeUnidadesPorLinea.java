package co.tecnosport.api.domain.compartido;

/**
 * Cuántas unidades de una misma variante caben en una línea, del carrito o del pedido (decisión del
 * 27/10/2026: 5). Sin tope, cualquiera podía apartar todas las unidades de una variante con pedidos
 * anónimos que nunca paga: la reserva dura lo que dura el pago, y un pedido contraentrega la deja
 * apartada hasta que alguien lo verifica.
 *
 * <p>El número es configuración ({@code tecnosport.pedido.maximo-unidades-por-linea}), no una
 * constante: es un dato del negocio que puede cambiar sin tocar el código. Aquí solo vive la regla.
 */
public record TopeDeUnidadesPorLinea(int maximo) {

  public TopeDeUnidadesPorLinea {
    if (maximo <= 0) {
      throw new IllegalArgumentException("El tope de unidades por línea tiene que ser positivo.");
    }
  }

  /** La cantidad de una línea, o la excepción si se pasa del tope. */
  public void exigir(int cantidad) {
    if (cantidad > maximo) {
      throw new CantidadPorLineaExcedidaException(maximo);
    }
  }
}
