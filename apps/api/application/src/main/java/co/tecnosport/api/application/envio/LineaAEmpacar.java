package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.UUID;

/**
 * Qué variante, cuántas, y por cuánto responde la transportadora si la pierde.
 *
 * <p>El peso, las medidas y la línea de catálogo salen del producto y nunca del cliente: si pudiera
 * declararlos, pagaría el flete de una camiseta por una caja de tenis.
 *
 * <p>{@code valorDeclarado} es la excepción, y viene de fuera porque <strong>depende de quién
 * pregunta</strong>. El checkout cotiza un carrito que todavía no es pedido y no tiene otro precio
 * que el del catálogo. El despacho sí lo tiene: el precio congelado en la línea, que es el que el
 * comprador pagó y el que aparece en la factura contra la que se reclama una pérdida.
 *
 * <p>Nulo significa "usa el del catálogo", y no puede ser un {@code Optional} porque el accesor de
 * un componente de {@code record} devuelve el tipo del componente.
 */
public record LineaAEmpacar(UUID varianteId, int cantidad, Dinero valorDeclarado) {

  public LineaAEmpacar {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
  }

  /** Para el checkout, que todavía no tiene un precio congelado del que tirar. */
  public LineaAEmpacar(UUID varianteId, int cantidad) {
    this(varianteId, cantidad, null);
  }
}
