package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.CrearProductoComando;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ListarProductosAdminComando;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.presentation.catalogo.dto.CrearProductoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductosAdminPaginadosRespuesta;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap) — este controlador no repite esa regla, mismo criterio que {@code
 * AdminPedidosControlador}.
 */
@RestController
@RequestMapping("/api/v1/admin/productos")
public class AdminProductoControlador {

  private static final int TAMANO_PAGINA_PREDETERMINADO = 20;

  private final ListarProductosAdmin listarProductosAdmin;
  private final CrearProducto crearProducto;
  private final MapeadorRespuestasProductoAdmin mapeador;

  public AdminProductoControlador(
      ListarProductosAdmin listarProductosAdmin,
      CrearProducto crearProducto,
      MapeadorRespuestasProductoAdmin mapeador) {
    this.listarProductosAdmin = Objects.requireNonNull(listarProductosAdmin);
    this.crearProducto = Objects.requireNonNull(crearProducto);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ProductosAdminPaginadosRespuesta listar(
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {
    ProductosPaginados resultado =
        listarProductosAdmin.ejecutar(new ListarProductosAdminComando(pagina, tamano));
    return mapeador.aRespuesta(resultado);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProductoAdminRespuesta crear(@RequestBody CrearProductoPeticion cuerpo) {
    Producto producto =
        crearProducto.ejecutar(
            new CrearProductoComando(
                cuerpo.nombre(), cuerpo.descripcion(), cuerpo.marcaId(), cuerpo.categoriaId()));
    return mapeador.aRespuesta(producto);
  }
}
