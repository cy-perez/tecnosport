package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.Tallas;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que una persona decide al aprobar: el título y la descripción finales, dónde cuelga en el
 * catálogo, a cuánto se vende, cómo talla, con cuánta existencia inicial nace cada variante, los
 * textos alternativos de la foto principal, qué tono muestra cada foto y cuáles son la misma
 * prenda.
 *
 * @param fotos en el orden en que van a publicarse; la primera es la principal
 * @param existenciaInicial por variante; el checkout no vende lo que el libro no tiene
 */
public record AprobarBorradorComando(
    UUID borradorId,
    String titulo,
    String descripcion,
    UUID categoriaId,
    UUID marcaId,
    long precioVenta,
    Tallas tallas,
    int existenciaInicial,
    String altEs,
    String altEn,
    List<FotoAprobada> fotos) {

  public AprobarBorradorComando {
    Objects.requireNonNull(borradorId, "El id del borrador no puede ser nulo.");
    Objects.requireNonNull(categoriaId, "La categoría es obligatoria.");
    Objects.requireNonNull(marcaId, "La marca es obligatoria.");
    if (precioVenta <= 0) {
      throw new IllegalArgumentException("El precio de venta tiene que ser mayor que cero.");
    }
    if (existenciaInicial < 0) {
      throw new IllegalArgumentException("La existencia inicial no puede ser negativa.");
    }
    fotos = fotos == null ? List.of() : List.copyOf(fotos);
  }

  /**
   * @param tono el color que muestra la foto, o nulo si es del producto entero
   * @param prenda a qué prenda del borrador pertenece la foto, desde 1: las fotos con el mismo
   *     número son la misma variante —vistas distintas de una prenda— y llevan el mismo tono. Nulo
   *     = una foto con tono es una prenda ella sola, que es lo que pasaba antes de existir el campo
   */
  public record FotoAprobada(UUID mensajeId, String tono, String colorHex, Integer prenda) {
    public FotoAprobada {
      Objects.requireNonNull(mensajeId, "La foto se nombra por su mensaje.");
      tono = tono == null || tono.isBlank() ? null : tono.strip();
      colorHex = colorHex == null || colorHex.isBlank() ? null : colorHex.strip();
      if (prenda != null && prenda < 1) {
        throw new IllegalArgumentException("Las prendas se numeran desde 1.");
      }
    }

    /** Una foto que no dice de qué prenda es: con tono, es una prenda ella sola. */
    public FotoAprobada(UUID mensajeId, String tono, String colorHex) {
      this(mensajeId, tono, colorHex, null);
    }
  }
}
