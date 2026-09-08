package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Slug;
import java.time.Instant;
import java.util.Objects;

/**
 * Una fila del mapa del sitio: lo mínimo para construir una entrada de {@code sitemap.xml}, y nada
 * más.
 *
 * <p>Vive en {@code application} y no en {@code domain} por lo mismo que {@link
 * ProductosPaginados}: no es un concepto del negocio sino la forma de una lectura concreta. Un
 * producto tiene marca, variantes, precio e imágenes; nada de eso le sirve a un rastreador, y
 * traerlo obligaría a hidratar el agregado entero para tirar el 95%.
 */
public record EntradaMapaDelSitio(Slug slug, Instant actualizadoEn) {

  public EntradaMapaDelSitio {
    Objects.requireNonNull(slug, "El slug no puede ser nulo.");
    Objects.requireNonNull(actualizadoEn, "La fecha de actualización no puede ser nula.");
  }
}
