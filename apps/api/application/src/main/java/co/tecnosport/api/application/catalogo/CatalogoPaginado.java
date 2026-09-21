package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.application.inventario.VariantesDisponibles;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

/**
 * Una página de la vitrina con la disponibilidad de sus variantes al lado.
 *
 * <p>Van juntas y no dentro de {@link Producto} porque la disponibilidad no es un atributo del
 * catálogo: es una lectura del libro de inventario hecha en el instante en que se pintó la página
 * (adr/0050). Meterla en el agregado obligaría a que todo el que carga un producto —el panel, el
 * mapa del sitio, el checkout— cargara también su inventario.
 */
public record CatalogoPaginado(
    ResultadoPaginado<Producto> pagina, VariantesDisponibles disponibles) {

  public CatalogoPaginado {
    Objects.requireNonNull(pagina, "La página del catálogo no puede ser nula.");
    Objects.requireNonNull(disponibles, "La disponibilidad no puede ser nula.");
  }
}
