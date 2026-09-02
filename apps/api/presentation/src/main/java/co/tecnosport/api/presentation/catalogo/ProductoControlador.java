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
            linea == null ? null : LineaCatalogo.valueOf(linea.toUpperCase(Locale.ROOT)),
            precioMin,
            precioMax,
            texto);
    BuscarProductosComando comando =
        new BuscarProductosComando(
            filtro, OrdenProductos.valueOf(orden.toUpperCase(Locale.ROOT)), cursor, tamano);

    return mapeador.aRespuesta(buscarProductos.ejecutar(comando));
  }

  @GetMapping("/{slug}")
  public ProductoRespuesta verFicha(@PathVariable String slug) {
    return mapeador.aRespuesta(
        verFichaDeProducto.ejecutar(new VerFichaDeProductoComando(new Slug(slug))));
  }
}
