package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;

/** Todo campo nulo significa "sin ese filtro". */
public record FiltroProductos(
    Slug categoriaSlug,
    UUID marcaId,
    LineaCatalogo linea,
    Long precioMinimo,
    Long precioMaximo,
    String texto) {

  public static FiltroProductos vacio() {
    return new FiltroProductos(null, null, null, null, null, null);
  }
}
