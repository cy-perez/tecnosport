package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.AgregarImagenDeGaleria;
import co.tecnosport.api.application.catalogo.AgregarImagenDeGaleriaComando;
import co.tecnosport.api.application.catalogo.ConfirmacionDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipal;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipalComando;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.CrearProductoComando;
import co.tecnosport.api.application.catalogo.DespublicarProducto;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.EditarProductoComando;
import co.tecnosport.api.application.catalogo.ImagenDeGaleriaQuitada;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ListarProductosAdminComando;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.PublicarProducto;
import co.tecnosport.api.application.catalogo.QuitarImagenDeGaleria;
import co.tecnosport.api.application.catalogo.QuitarImagenDeGaleriaComando;
import co.tecnosport.api.application.catalogo.ReordenarGaleria;
import co.tecnosport.api.application.catalogo.ReordenarGaleriaComando;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenDeGaleria;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenDeGaleriaComando;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipalComando;
import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.catalogo.VarianteSubida;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.presentation.catalogo.dto.AgregarImagenDeGaleriaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.ConfirmarImagenPrincipalPeticion;
import co.tecnosport.api.presentation.catalogo.dto.CrearProductoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.EditarProductoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.ImagenDeGaleriaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ImagenRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminDetalleRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductosAdminPaginadosRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ReordenarGaleriaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.SolicitarSubidaDeImagenDeGaleriaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.SolicitarSubidaDeImagenPrincipalPeticion;
import co.tecnosport.api.presentation.catalogo.dto.UrlSubidaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VarianteSubidaPeticion;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
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
  private final SolicitarSubidaDeImagenDeGaleria solicitarSubidaDeImagenDeGaleria;
  private final AgregarImagenDeGaleria agregarImagenDeGaleria;
  private final QuitarImagenDeGaleria quitarImagenDeGaleria;
  private final ReordenarGaleria reordenarGaleria;
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
      SolicitarSubidaDeImagenDeGaleria solicitarSubidaDeImagenDeGaleria,
      AgregarImagenDeGaleria agregarImagenDeGaleria,
      QuitarImagenDeGaleria quitarImagenDeGaleria,
      ReordenarGaleria reordenarGaleria,
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
    this.solicitarSubidaDeImagenDeGaleria =
        Objects.requireNonNull(solicitarSubidaDeImagenDeGaleria);
    this.agregarImagenDeGaleria = Objects.requireNonNull(agregarImagenDeGaleria);
    this.quitarImagenDeGaleria = Objects.requireNonNull(quitarImagenDeGaleria);
    this.reordenarGaleria = Objects.requireNonNull(reordenarGaleria);
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
  public ProductoAdminDetalleRespuesta ver(@PathVariable("id") UUID id) {
    return mapeador.aDetalle(verProductoAdmin.ejecutar(id));
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
                aVariantesSubidas(cuerpo.variantes()),
                cuerpo.objectKeyVistaPrevia(),
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

  @PostMapping("/{id}/galeria/url-subida")
  @ResponseStatus(HttpStatus.CREATED)
  public UrlSubidaRespuesta solicitarUrlDeSubidaDeGaleria(
      @PathVariable("id") UUID id, @RequestBody SolicitarSubidaDeImagenDeGaleriaPeticion cuerpo) {
    SolicitudDeSubida solicitud =
        solicitarSubidaDeImagenDeGaleria.ejecutar(
            new SolicitarSubidaDeImagenDeGaleriaComando(id, cuerpo.contentType()));
    return new UrlSubidaRespuesta(solicitud.url(), solicitud.objectKey());
  }

  /**
   * {@code 201} y no {@code 200} como la principal: allá se reemplaza algo que ya existía, aquí
   * nace una imagen nueva que antes no estaba.
   */
  @PostMapping("/{id}/galeria")
  @ResponseStatus(HttpStatus.CREATED)
  public ImagenDeGaleriaRespuesta agregarImagenDeGaleria(
      @PathVariable("id") UUID id, @RequestBody AgregarImagenDeGaleriaPeticion cuerpo) {
    ImagenProducto imagen =
        agregarImagenDeGaleria.ejecutar(
            new AgregarImagenDeGaleriaComando(
                id,
                aVariantesSubidas(cuerpo.variantes()),
                cuerpo.objectKeyVistaPrevia(),
                cuerpo.alto(),
                cuerpo.hash(),
                cuerpo.altEs(),
                cuerpo.altEn()));
    log.info("Producto {}: imagen agregada a la galería en el orden {}.", id, imagen.orden());
    return mapeador.aRespuestaDeGaleria(imagen);
  }

  /**
   * {@code 204} y no la galería que queda: el panel vuelve a pedir el producto igual, y devolver
   * una lista aquí invitaría a creerle a esta respuesta en vez de a la consulta.
   *
   * <p><b>Sin {@code TransactionTemplate}, y eso es una corrección.</b> Lo tuvo, con el argumento
   * de que un borrado derivado de Spring Data no trae transacción propia — cierto, pero la trae
   * {@code RepositorioProductosJpa.eliminarImagenDeGaleria}, que es donde toca. Envolver el caso de
   * uso entero metía además la llamada a Cloud Storage dentro de la transacción, y con eso el orden
   * que el caso de uso promete —primero la fila, después el objeto— dejaba de estar garantizado: el
   * objeto se borraba antes del commit.
   */
  @DeleteMapping("/{id}/galeria/{imagenId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void quitarImagenDeGaleria(
      @PathVariable("id") UUID id, @PathVariable("imagenId") UUID imagenId) {
    ImagenDeGaleriaQuitada quitada =
        quitarImagenDeGaleria.ejecutar(new QuitarImagenDeGaleriaComando(id, imagenId));
    if (quitada.limpiezaFallida()) {
      log.error(
          "Producto {}: la imagen {} salió de la galería, pero no se pudo borrar su objeto del"
              + " bucket. Queda un archivo sin reclamar.",
          id,
          imagenId);
    } else if (!quitada.objetoBorrado()) {
      // No es un error, pero tampoco es rutina, y el día que deje de ser inofensivo hará falta esta
      // línea: si cambia la URL pública del bucket —por ponerlo detrás de un CDN, por ejemplo—,
      // ninguna URL vieja se reconocería y cada borrado se saldría por aquí sin borrar nada.
      log.warn(
          "Producto {}: la imagen {} salió de la galería sin borrar ningún objeto. O su URL no es"
              + " de este almacén, o el objeto ya no estaba.",
          id,
          imagenId);
    } else {
      log.info("Producto {}: imagen {} retirada de la galería, con su objeto.", id, imagenId);
    }
  }

  /**
   * {@code PUT} y no {@code PATCH}: lo que se manda es el estado completo del orden de la galería,
   * no un cambio parcial sobre él. Con eso, mandarlo dos veces deja lo mismo.
   *
   * <p>{@code 204} y no la galería que queda, igual que quitar y por el mismo motivo: el panel
   * vuelve a pedir el producto, y devolver la lista aquí invitaría a creerle a esta respuesta en
   * vez de a la consulta.
   */
  @PutMapping("/{id}/galeria/orden")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reordenarGaleria(
      @PathVariable("id") UUID id, @RequestBody ReordenarGaleriaPeticion cuerpo) {
    List<ImagenProducto> galeria =
        reordenarGaleria.ejecutar(new ReordenarGaleriaComando(id, cuerpo.imagenIds()));
    log.info("Producto {}: galería reordenada, {} imágenes.", id, galeria.size());
  }

  /** El DTO de entrada no cruza a la capa de aplicación: se traduce aquí, como todos los demás. */
  private static List<VarianteSubida> aVariantesSubidas(List<VarianteSubidaPeticion> variantes) {
    return variantes.stream().map(v -> new VarianteSubida(v.ancho(), v.objectKey())).toList();
  }
}
