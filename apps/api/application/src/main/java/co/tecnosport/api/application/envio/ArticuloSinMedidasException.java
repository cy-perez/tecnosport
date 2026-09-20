package co.tecnosport.api.application.envio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un artículo del carrito todavía no se ha medido, así que no se puede cotizar y no va a domicilio
 * ({@code adr/0046}). No es una falla ni algo que se arregle reintentando: el checkout lo traduce a
 * recogida en el punto, nombrando el artículo.
 *
 * <p>Es la hermana de {@link ArticuloNoAsegurableException} y se comporta igual a propósito — misma
 * forma, mismos sitios donde se atrapa, misma traducción a 409. Para quien compra las dos son la
 * misma frase: "esto no te lo podemos enviar, pero lo puedes recoger". Lo que las separa es a quién
 * le toca arreglarlo: aquella no se arregla nunca —el artículo vale más de lo que la transportadora
 * asegura— y esta se arregla sola en cuanto alguien pase el producto por la báscula.
 *
 * <p>Por eso el mensaje no le habla al comprador de medidas ni de básculas: es un detalle interno
 * del negocio, y decirle "nos falta pesarlo" es contarle un problema nuestro en vez de darle la
 * opción que sí tiene.
 *
 * <p>Se listan <strong>todos</strong> los culpables, no el primero: quitar uno y volver a chocar
 * con el siguiente es la forma de que alguien abandone el carrito a la segunda.
 */
public final class ArticuloSinMedidasException extends RuntimeException {

  /** La variante culpable, con lo justo para nombrarla. */
  public record Articulo(UUID varianteId, String nombre) {

    public Articulo {
      Objects.requireNonNull(varianteId, "La variante de un artículo no puede ser nula.");
      if (nombre == null || nombre.isBlank()) {
        throw new IllegalArgumentException("El nombre de un artículo no puede estar vacío.");
      }
    }
  }

  private final transient List<Articulo> articulos;

  public ArticuloSinMedidasException(List<Articulo> articulos) {
    super(mensaje(articulos));
    this.articulos = List.copyOf(articulos);
  }

  public List<Articulo> articulos() {
    return articulos;
  }

  private static String mensaje(List<Articulo> articulos) {
    Objects.requireNonNull(articulos, "Los artículos sin medidas no pueden ser nulos.");
    if (articulos.isEmpty()) {
      throw new IllegalArgumentException(
          "Un artículo sin medidas tiene que decir cuál es: la lista no puede estar vacía.");
    }
    return String.join(", ", articulos.stream().map(Articulo::nombre).toList())
        + " no se puede despachar a domicilio todavía.";
  }
}
