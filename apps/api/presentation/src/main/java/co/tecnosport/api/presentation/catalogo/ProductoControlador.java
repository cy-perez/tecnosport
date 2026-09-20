package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.BuscarProductos;
import co.tecnosport.api.application.catalogo.BuscarProductosComando;
import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.VerFichaDeProducto;
import co.tecnosport.api.application.catalogo.VerFichaDeProductoComando;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.presentation.catalogo.dto.ProductoRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoControlador {

  private static final int TAMANO_PAGINA_PREDETERMINADO = 24;

  private final BuscarProductos buscarProductos;
  private final VerFichaDeProducto verFichaDeProducto;
  private final MapeadorRespuestasCatalogo mapeador;

  public ProductoControlador(
      BuscarProductos buscarProductos,
      VerFichaDeProducto verFichaDeProducto,
      MapeadorRespuestasCatalogo mapeador) {
    this.buscarProductos = Objects.requireNonNull(buscarProductos);
    this.verFichaDeProducto = Objects.requireNonNull(verFichaDeProducto);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<ProductoRespuesta> buscar(
      @RequestParam(required = false) String categoria,
      @RequestParam(required = false) UUID marca,
      @RequestParam(required = false) String linea,
      @RequestParam(required = false) Long precioMin,
      @RequestParam(required = false) Long precioMax,
      @RequestParam(required = false) String texto,
      @RequestParam(defaultValue = "RELEVANCIA") String orden,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {

    FiltroProductos filtro =
        new FiltroProductos(
            categoria == null ? null : new Slug(categoria),
            marca,
            aLinea(linea),
            precioMin,
            precioMax,
            texto);
    BuscarProductosComando comando =
        new BuscarProductosComando(
            filtro, OrdenProductos.valueOf(orden.toUpperCase(Locale.ROOT)), cursor, tamano);

    return mapeador.aRespuesta(buscarProductos.ejecutar(comando).pagina());
  }

  /**
   * El nombre de la línea, con el alias de la que se renombró.
   *
   * <p>{@code CELULARES} fue una línea hasta el 14 de septiembre de 2026 y hoy es una categoría
   * dentro de {@code TECNOLOGIA}. Un enlace compartido o indexado con {@code ?linea=CELULARES}
   * seguiría existiendo mucho después del cambio, y sin esto respondería un error de filtro
   * inválido: el comprador vería una página rota por una decisión interna de modelado que no le
   * incumbe. Se traduce en silencio, que es lo que hace un alias.
   *
   * <p>Una redirección 301 sería más correcta de cara a un buscador, pero toca el SSR y el
   * enrutador del frontend; el alias resuelve el caso del comprador con dos líneas y ninguna
   * dependencia nueva.
   */
  private static LineaCatalogo aLinea(String linea) {
    if (linea == null) {
      return null;
    }
    String nombre = linea.trim().toUpperCase(Locale.ROOT);
    return LineaCatalogo.valueOf("CELULARES".equals(nombre) ? "TECNOLOGIA" : nombre);
  }

  @GetMapping("/{slug}")
  public ProductoRespuesta verFicha(@PathVariable String slug) {
    return mapeador.aRespuesta(
        verFichaDeProducto.ejecutar(new VerFichaDeProductoComando(new Slug(slug))).producto());
  }
}
