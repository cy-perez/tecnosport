package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.SinonimosDeBusqueda;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;

/**
 * Todo campo nulo significa "sin ese filtro". El texto llega con sus sinónimos ya llevados a como
 * los escribe el catálogo: «body» busca «bodi» ({@link SinonimosDeBusqueda}).
 *
 * <p>{@code talla} deja solo los productos de esa línea que tienen esa talla con existencia: no
 * basta con que la variante exista, tiene que poderse comprar ({@link FiltroDeTalla}).
 */
public record FiltroProductos(
    Slug categoriaSlug,
    UUID marcaId,
    LineaCatalogo linea,
    Long precioMinimo,
    Long precioMaximo,
    String texto,
    FiltroDeTalla talla) {

  public FiltroProductos {
    texto = SinonimosDeBusqueda.aplicar(texto);
  }

  /** Sin talla: la forma de todos los que filtraban antes de que existiera. */
  public FiltroProductos(
      Slug categoriaSlug,
      UUID marcaId,
      LineaCatalogo linea,
      Long precioMinimo,
      Long precioMaximo,
      String texto) {
    this(categoriaSlug, marcaId, linea, precioMinimo, precioMaximo, texto, null);
  }

  public static FiltroProductos vacio() {
    return new FiltroProductos(null, null, null, null, null, null, null);
  }
}
