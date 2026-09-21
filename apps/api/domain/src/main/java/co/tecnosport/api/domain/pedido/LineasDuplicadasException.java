package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * Dos líneas del mismo pedido apuntan a la misma variante.
 *
 * <p>El carrito no produce esto —reconcilia por variante y suma cantidades—, pero la API es pública
 * y la app móvil todavía no existe, así que el servidor no puede dar por supuesta la forma del
 * cliente. Se rechaza en vez de sumar las cantidades: sumarlas sería el servidor decidiendo por el
 * comprador algo que nadie le pidió, y una línea repetida por un defecto del cliente se convertiría
 * en una compra de dos unidades que alguien tiene que devolver.
 */
public final class LineasDuplicadasException extends ExcepcionDeDominio {

  public LineasDuplicadasException(UUID varianteId) {
    super("El pedido trae dos veces la misma variante: " + varianteId + ".");
  }
}
