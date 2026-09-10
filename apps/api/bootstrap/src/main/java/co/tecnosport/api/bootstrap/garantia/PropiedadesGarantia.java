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
 * <p>{@code categoriasSinTerminoConocido} es la pieza que impide inventar un dato: una categoría
 * declarada aquí responde "no se sabe" en vez de caer a los doce meses generales. <b>Hoy va
 * vacía</b>, y conviene saber por qué existe igual. Los celulares estuvieron en esta lista mientras
 * se creyó que su término era un dato del negocio, porque los términos publicados prometían "la
 * garantía del fabricante". No lo era: el término lo fija la ley —un año para producto nuevo, sin
 * régimen especial para equipos terminales— y lo que el productor anuncie solo cuenta si es mayor,
 * y entonces va en {@code mesesPorCategoria}. La lista se queda para el caso que sí la necesita:
 * una categoría cuyo término dependa de una norma que aquí no esté cargada.
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
