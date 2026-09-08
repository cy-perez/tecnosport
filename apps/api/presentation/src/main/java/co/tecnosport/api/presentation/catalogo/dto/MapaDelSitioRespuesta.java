package co.tecnosport.api.presentation.catalogo.dto;

import java.time.Instant;
import java.util.List;

/**
 * Alimenta el {@code sitemap.xml} que sirve el frontend. No devuelve URL sino slugs: las URL las
 * arma quien conoce las rutas y los prefijos de idioma, que es la web — el backend no sabe, ni
 * tiene por qué, que la ficha vive en {@code /{idioma}/productos/{slug}}. El día que exista la app
 * móvil, esa ruta no significará nada y este endpoint seguirá sirviendo.
 *
 * <p>Envuelto en un objeto y no como arreglo suelto en la raíz: deja sitio para las categorías o
 * las páginas estáticas sin romper a quien ya lo consume.
 */
public record MapaDelSitioRespuesta(List<Producto> productos) {

  /** {@code actualizadoEn} sale tal cual a {@code <lastmod>}; por eso viaja como instante ISO. */
  public record Producto(String slug, Instant actualizadoEn) {}
}
