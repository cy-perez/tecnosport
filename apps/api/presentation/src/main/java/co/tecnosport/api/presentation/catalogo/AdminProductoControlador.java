package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipal;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipalComando;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.CrearProductoComando;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.EditarProductoComando;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ListarProductosAdminComando;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipalComando;
import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.presentation.catalogo.dto.ConfirmarImagenPrincipalPeticion;
import co.tecnosport.api.presentation.catalogo.dto.CrearProductoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.EditarProductoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.ImagenRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductosAdminPaginadosRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.SolicitarSubidaDeImagenPrincipalPeticion;
import co.tecnosport.api.presentation.catalogo.dto.UrlSubidaRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final VerProductoAdmin verProductoAdmin;
  private final EditarProducto editarProducto;
  private final SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal;
  private final ConfirmarImagenPrincipal confirmarImagenPrincipal;
  private final MapeadorRespuestasProductoAdmin mapeador;

  public AdminProductoControlador(
      ListarProductosAdmin listarProductosAdmin,
      CrearProducto crearProducto,
      VerProductoAdmin verProductoAdmin,
      EditarProducto editarProducto,
      SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal,
      ConfirmarImagenPrincipal confirmarImagenPrincipal,
      MapeadorRespuestasProductoAdmin mapeador) {
    this.listarProductosAdmin = Objects.requireNonNull(listarProductosAdmin);
    this.crearProducto = Objects.requireNonNull(crearProducto);
    this.verProductoAdmin = Objects.requireNonNull(verProductoAdmin);
    this.editarProducto = Objects.requireNonNull(editarProducto);
    this.solicitarSubidaDeImagenPrincipal =
        Objects.requireNonNull(solicitarSubidaDeImagenPrincipal);
    this.confirmarImagenPrincipal = Objects.requireNonNull(confirmarImagenPrincipal);
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

  @GetMapping("/{id}")
  public ProductoAdminRespuesta ver(@PathVariable("id") UUID id) {
    return mapeador.aRespuesta(verProductoAdmin.ejecutar(id));
  }

  @PatchMapping("/{id}")
  public ProductoAdminRespuesta editar(
      @PathVariable("id") UUID id, @RequestBody EditarProductoPeticion cuerpo) {
    Producto producto =
        editarProducto.ejecutar(
            new EditarProductoComando(
                id, cuerpo.nombre(), cuerpo.descripcion(), cuerpo.marcaId(), cuerpo.categoriaId()));
    return mapeador.aRespuesta(producto);
  }

  @PostMapping("/{id}/imagen-principal/url-subida")
  @ResponseStatus(HttpStatus.CREATED)
  public UrlSubidaRespuesta solicitarUrlDeSubida(
      @PathVariable("id") UUID id, @RequestBody SolicitarSubidaDeImagenPrincipalPeticion cuerpo) {
    SolicitudDeSubida solicitud =
        solicitarSubidaDeImagenPrincipal.ejecutar(
            new SolicitarSubidaDeImagenPrincipalComando(id, cuerpo.contentType()));
    return new UrlSubidaRespuesta(solicitud.url(), solicitud.objectKey());
  }

  @PostMapping("/{id}/imagen-principal")
  public ImagenRespuesta confirmarImagenPrincipal(
      @PathVariable("id") UUID id, @RequestBody ConfirmarImagenPrincipalPeticion cuerpo) {
    ImagenProducto imagen =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                id,
                cuerpo.objectKey(),
                cuerpo.ancho(),
                cuerpo.alto(),
                cuerpo.altEs(),
                cuerpo.altEn()));
    return mapeador.aRespuesta(imagen);
  }
}
