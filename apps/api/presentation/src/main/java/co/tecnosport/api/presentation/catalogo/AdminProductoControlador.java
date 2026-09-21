package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ConfirmacionDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipal;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipalComando;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.CrearProductoComando;
import co.tecnosport.api.application.catalogo.DespublicarProducto;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.EditarProductoComando;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ListarProductosAdminComando;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.PublicarProducto;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipalComando;
import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

  private static final Logger log = LoggerFactory.getLogger(AdminProductoControlador.class);

  private static final int TAMANO_PAGINA_PREDETERMINADO = 20;

  private final ListarProductosAdmin listarProductosAdmin;
  private final CrearProducto crearProducto;
  private final VerProductoAdmin verProductoAdmin;
  private final EditarProducto editarProducto;
  private final SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal;
  private final ConfirmarImagenPrincipal confirmarImagenPrincipal;
  private final PublicarProducto publicarProducto;
  private final DespublicarProducto despublicarProducto;
  private final MapeadorRespuestasProductoAdmin mapeador;

  public AdminProductoControlador(
      ListarProductosAdmin listarProductosAdmin,
      CrearProducto crearProducto,
      VerProductoAdmin verProductoAdmin,
      EditarProducto editarProducto,
      SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal,
      ConfirmarImagenPrincipal confirmarImagenPrincipal,
      PublicarProducto publicarProducto,
      DespublicarProducto despublicarProducto,
      MapeadorRespuestasProductoAdmin mapeador) {
    this.listarProductosAdmin = Objects.requireNonNull(listarProductosAdmin);
    this.crearProducto = Objects.requireNonNull(crearProducto);
    this.verProductoAdmin = Objects.requireNonNull(verProductoAdmin);
    this.editarProducto = Objects.requireNonNull(editarProducto);
    this.solicitarSubidaDeImagenPrincipal =
        Objects.requireNonNull(solicitarSubidaDeImagenPrincipal);
    this.confirmarImagenPrincipal = Objects.requireNonNull(confirmarImagenPrincipal);
    this.publicarProducto = Objects.requireNonNull(publicarProducto);
    this.despublicarProducto = Objects.requireNonNull(despublicarProducto);
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

  /**
   * {@code POST} sobre un subrecurso y no un {@code PATCH} del estado: publicar no es editar un
   * campo, es una transición con su propia regla —no hay publicación sin imagen principal— y su
   * propio permiso conceptual. Mismo criterio que {@code /sets-rotacion/{id}/publicar}.
   */
  @PostMapping("/{id}/publicacion")
  public ProductoAdminRespuesta publicar(@PathVariable("id") UUID id) {
    Producto producto = publicarProducto.ejecutar(id);
    log.info("Producto publicado: {}", id);
    return mapeador.aRespuesta(producto);
  }

  /**
   * {@code DELETE} sobre el mismo subrecurso que lo creó: se borra la publicación, no el producto
   * —que sigue ahí, en {@code BORRADOR}—. Es la forma que ya tiene sentido en esta API para
   * deshacer una transición, y evita inventar un {@code /despublicacion} que nombraría un recurso
   * que no existe.
   *
   * <p>Se registra como {@code warn} y no como {@code info}: publicar es rutina, retirar algo de la
   * vitrina no. Si alguien pregunta mañana por qué un producto dejó de verse, esta línea es la
   * respuesta.
   */
  @DeleteMapping("/{id}/publicacion")
  public ProductoAdminRespuesta despublicar(@PathVariable("id") UUID id) {
    Producto producto = despublicarProducto.ejecutar(id);
    log.warn("Producto retirado de la vitrina: {}", id);
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
    ConfirmacionDeImagenPrincipal confirmacion =
        confirmarImagenPrincipal.ejecutar(
            new ConfirmarImagenPrincipalComando(
                id,
                cuerpo.objectKey(),
                cuerpo.ancho(),
                cuerpo.alto(),
                cuerpo.hash(),
                cuerpo.altEs(),
                cuerpo.altEn()));
    if (confirmacion.limpiezaFallida()) {
      log.error(
          "Producto {}: la imagen principal se guardó, pero no se pudieron borrar las anteriores"
              + " del bucket. Quedan objetos sin reclamar bajo 'productos/{}/principal-'.",
          id,
          id);
    } else {
      log.info(
          "Producto {}: imagen principal reemplazada; {} objetos anteriores borrados del bucket.",
          id,
          confirmacion.objetosAnterioresBorrados());
    }
    return mapeador.aRespuesta(confirmacion.imagen());
  }
}
