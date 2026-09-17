package co.tecnosport.api.application.envio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un artículo del carrito vale más de lo que la transportadora puede asegurar, así que no se
 * despacha a domicilio ({@code adr/0036}). No es una falla ni algo que se arregle reintentando: el
 * checkout lo traduce a recogida en el punto, nombrando el artículo.
 *
 * <p><strong>Lleva los artículos culpables y no solo un mensaje</strong> porque el comprador tiene
 * que saber cuál de las cosas que puso en el carrito le cambió la entrega. "Algo de tu pedido no se
 * puede enviar" es casi tan inútil como el "intenta más tarde" que esta excepción vino a
 * reemplazar, y el que lo lee está mirando una lista de cinco productos.
 *
 * <p>Se listan <strong>todos</strong>, no el primero que aparezca: quitar uno y volver a chocar con
 * el siguiente es la forma de que alguien abandone el carrito a la segunda.
 */
public final class ArticuloNoAsegurableException extends RuntimeException {

  /** La variante culpable, con lo justo para nombrarla: el resto lo tiene el cliente. */
  public record Articulo(UUID varianteId, String nombre) {

    public Articulo {
      Objects.requireNonNull(varianteId, "La variante de un artículo no puede ser nula.");
      if (nombre == null || nombre.isBlank()) {
        throw new IllegalArgumentException("El nombre de un artículo no puede estar vacío.");
      }
    }
  }

  private final transient List<Articulo> articulos;

  public ArticuloNoAsegurableException(List<Articulo> articulos) {
    super(mensaje(articulos));
    this.articulos = List.copyOf(articulos);
  }

  public List<Articulo> articulos() {
    return articulos;
  }

  private static String mensaje(List<Articulo> articulos) {
    Objects.requireNonNull(articulos, "Los artículos no asegurables no pueden ser nulos.");
    if (articulos.isEmpty()) {
      throw new IllegalArgumentException(
          "Un artículo no asegurable tiene que decir cuál es: la lista no puede estar vacía.");
    }
    return "El valor de "
        + String.join(", ", articulos.stream().map(Articulo::nombre).toList())
        + " supera el máximo asegurable por la transportadora.";
  }
}
