package co.tecnosport.api.bootstrap.garantia;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El término de la garantía legal, en meses, por categoría.
 *
 * <p>{@code mesesPorDefecto} son los doce meses que prometen los términos publicados para producto
 * nuevo. {@code mesesPorCategoria} permite el término mayor que anuncie un productor concreto.
 *
 * <p>{@code categoriasSinTerminoConocido} es la pieza que impide inventar un dato. Los términos
 * dicen que para teléfonos celulares aplica la garantía del fabricante, y ese plazo sigue marcado
 * como pendiente ({@code [[GARANTÍA DE CELULARES]]}). Su categoría se declara aquí y responde "no
 * se sabe" en vez de caer a los doce meses generales — que sería inventarlo. El día que llegue el
 * dato, se mueve de esta lista a {@code mesesPorCategoria} y nada más cambia.
 */
@ConfigurationProperties(prefix = "tecnosport.garantia")
public record PropiedadesGarantia(
    int mesesPorDefecto,
    Map<String, Integer> mesesPorCategoria,
    List<String> categoriasSinTerminoConocido) {

  public PropiedadesGarantia {
    if (mesesPorDefecto <= 0) {
      throw new IllegalStateException(
          "tecnosport.garantia.meses-por-defecto debe ser mayor que cero.");
    }
    mesesPorCategoria = mesesPorCategoria == null ? Map.of() : Map.copyOf(mesesPorCategoria);
    categoriasSinTerminoConocido =
        categoriasSinTerminoConocido == null
            ? List.of()
            : List.copyOf(categoriasSinTerminoConocido);
  }
}
