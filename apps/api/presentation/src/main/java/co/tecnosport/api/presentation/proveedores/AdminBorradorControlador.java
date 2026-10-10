package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.proveedores.AprobarBorrador;
import co.tecnosport.api.application.proveedores.AprobarBorradorComando;
import co.tecnosport.api.application.proveedores.ConfirmarFotoDeBorrador;
import co.tecnosport.api.application.proveedores.DescartarFotoDeBorrador;
import co.tecnosport.api.application.proveedores.EditarBorrador;
import co.tecnosport.api.application.proveedores.EditarBorradorComando;
import co.tecnosport.api.application.proveedores.EliminarBorrador;
import co.tecnosport.api.application.proveedores.EliminarBorradoresSinAprobar;
import co.tecnosport.api.application.proveedores.PartirBorrador;
import co.tecnosport.api.application.proveedores.RechazarBorrador;
import co.tecnosport.api.application.proveedores.RepositorioBorradores;
import co.tecnosport.api.application.proveedores.SolicitarSubidaDeFotoDeBorrador;
import co.tecnosport.api.application.proveedores.VerBorrador;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import co.tecnosport.api.presentation.catalogo.MapeadorRespuestasProductoAdmin;
import co.tecnosport.api.presentation.catalogo.dto.ProductoAdminRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.AprobarBorradorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.BorradorDetalleRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.BorradorRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.BorradoresEliminadosRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.BorradoresPaginadosRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.BorradoresSinAprobarRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.ConfirmarFotoPeticion;
import co.tecnosport.api.presentation.proveedores.dto.EditarBorradorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.PartirBorradorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.RechazarBorradorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.SolicitarSubidaDeFotoPeticion;
import co.tecnosport.api.presentation.proveedores.dto.SubidaDeFotoRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
 * La bandeja de borradores: listar, ver, editar, subir y descartar fotos, aprobar, rechazar y
 * borrar.
 *
 * <p>Aprobar corre en una transacción del controlador aunque copie fotos al bucket en la mitad: si
 * algo falla después de copiar, quedan objetos sueltos en el bucket y ningún producto a medias en
 * la base, que es el lado bueno del que caerse. La limpieza de esos objetos es la misma que hace el
 * reemplazo de la principal.
 */
@RestController
@RequestMapping("/api/v1/admin/borradores")
public class AdminBorradorControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminBorradorControlador.class);
  private static final int TAMANO_PAGINA_PREDETERMINADO = 20;

  /**
   * Cuántos borradores borra una petición del borrado en bloque. Cada uno son unas pocas filas y
   * uno o dos objetos del bucket: cien caben con holgura en el límite de tiempo del servidor.
   */
  static final int TANDA_DE_BORRADO = 100;

  private final RepositorioBorradores repositorioBorradores;
  private final VerBorrador verBorrador;
  private final EditarBorrador editarBorrador;
  private final AprobarBorrador aprobarBorrador;
  private final RechazarBorrador rechazarBorrador;
  private final EliminarBorrador eliminarBorrador;
  private final EliminarBorradoresSinAprobar eliminarSinAprobar;
  private final DescartarFotoDeBorrador descartarFotoDeBorrador;
  private final SolicitarSubidaDeFotoDeBorrador solicitarSubidaDeFoto;
  private final ConfirmarFotoDeBorrador confirmarFoto;
  private final PartirBorrador partirBorrador;
  private final MapeadorRespuestasProductoAdmin mapeadorProducto;
  private final TransactionTemplate transaccion;

  public AdminBorradorControlador(
      RepositorioBorradores repositorioBorradores,
      VerBorrador verBorrador,
      EditarBorrador editarBorrador,
      AprobarBorrador aprobarBorrador,
      RechazarBorrador rechazarBorrador,
      EliminarBorrador eliminarBorrador,
      EliminarBorradoresSinAprobar eliminarSinAprobar,
      DescartarFotoDeBorrador descartarFotoDeBorrador,
      SolicitarSubidaDeFotoDeBorrador solicitarSubidaDeFoto,
      ConfirmarFotoDeBorrador confirmarFoto,
      PartirBorrador partirBorrador,
      MapeadorRespuestasProductoAdmin mapeadorProducto,
      PlatformTransactionManager transactionManager) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.verBorrador = Objects.requireNonNull(verBorrador);
    this.editarBorrador = Objects.requireNonNull(editarBorrador);
    this.aprobarBorrador = Objects.requireNonNull(aprobarBorrador);
    this.rechazarBorrador = Objects.requireNonNull(rechazarBorrador);
    this.eliminarBorrador = Objects.requireNonNull(eliminarBorrador);
    this.eliminarSinAprobar = Objects.requireNonNull(eliminarSinAprobar);
    this.descartarFotoDeBorrador = Objects.requireNonNull(descartarFotoDeBorrador);
    this.solicitarSubidaDeFoto = Objects.requireNonNull(solicitarSubidaDeFoto);
    this.confirmarFoto = Objects.requireNonNull(confirmarFoto);
    this.partirBorrador = Objects.requireNonNull(partirBorrador);
    this.mapeadorProducto = Objects.requireNonNull(mapeadorProducto);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public BorradoresPaginadosRespuesta listar(
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) UUID proveedorId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "" + TAMANO_PAGINA_PREDETERMINADO) int tamano) {
    EstadoBorrador filtro =
        estado == null || estado.isBlank() ? null : EstadoBorrador.valueOf(estado);
    return BorradoresPaginadosRespuesta.de(
        repositorioBorradores.listar(filtro, proveedorId, pagina, tamano));
  }

  /** Cuántos borraría {@link #eliminarSinAprobar()}: el panel lo dice antes de confirmar. */
  @GetMapping("/sin-aprobar")
  public BorradoresSinAprobarRespuesta contarSinAprobar() {
    return new BorradoresSinAprobarRespuesta(eliminarSinAprobar.contar());
  }

  /**
   * Una tanda del borrado en bloque de los borradores en revisión o rechazados. Responde cuántos
   * quedan; el panel repite mientras no sea cero. {@code warn}, como el borrado de uno.
   */
  @DeleteMapping("/sin-aprobar")
  public BorradoresEliminadosRespuesta eliminarSinAprobar() {
    var resultado = transaccion.execute(estado -> eliminarSinAprobar.ejecutar(TANDA_DE_BORRADO));
    log.warn(
        "Borradores sin aprobar eliminados: {} ({} fotos borradas del bucket, quedan {})",
        resultado.eliminados(),
        resultado.archivosBorrados(),
        resultado.quedan());
    return BorradoresEliminadosRespuesta.de(resultado);
  }

  @GetMapping("/{id}")
  public BorradorDetalleRespuesta ver(@PathVariable UUID id) {
    return BorradorDetalleRespuesta.de(verBorrador.ejecutar(id));
  }

  @PatchMapping("/{id}")
  public BorradorRespuesta editar(
      @PathVariable UUID id, @RequestBody EditarBorradorPeticion cuerpo) {
    return BorradorRespuesta.de(
        transaccion.execute(
            estado ->
                editarBorrador.ejecutar(
                    new EditarBorradorComando(
                        id,
                        cuerpo.titulo(),
                        cuerpo.tipo() == null ? null : TipoProductoProveedor.valueOf(cuerpo.tipo()),
                        cuerpo.precioVentaSugerido() == null
                            ? null
                            : Dinero.deCop(cuerpo.precioVentaSugerido()),
                        cuerpo.tallas() == null ? null : cuerpo.tallas().aDominio(),
                        cuerpo.cantidadTonos(),
                        cuerpo.tonosNombrados(),
                        cuerpo.material(),
                        cuerpo.descripcion(),
                        cuerpo.altEn()))));
  }

  @PostMapping("/{id}/aprobar")
  public ProductoAdminRespuesta aprobar(
      @PathVariable UUID id, @RequestBody AprobarBorradorPeticion cuerpo) {
    Producto producto =
        transaccion.execute(
            estado ->
                aprobarBorrador.ejecutar(
                    new AprobarBorradorComando(
                        id,
                        cuerpo.titulo(),
                        cuerpo.descripcion(),
                        cuerpo.categoriaId(),
                        cuerpo.marcaId(),
                        cuerpo.precioVenta(),
                        cuerpo.tallas() == null ? null : cuerpo.tallas().aDominio(),
                        cuerpo.existenciaInicial(),
                        cuerpo.altEs(),
                        cuerpo.altEn(),
                        cuerpo.fotos().stream()
                            .map(
                                f ->
                                    new AprobarBorradorComando.FotoAprobada(
                                        f.mensajeId(), f.tono(), f.colorHex(), f.prenda()))
                            .toList())));
    log.info(
        "Borrador {} aprobado: producto {} publicado con {} variante(s)",
        id,
        producto.id(),
        producto.variantes().size());
    return mapeadorProducto.aRespuesta(producto);
  }

  @PostMapping("/{id}/rechazar")
  public BorradorRespuesta rechazar(
      @PathVariable UUID id, @RequestBody RechazarBorradorPeticion cuerpo) {
    return BorradorRespuesta.de(
        transaccion.execute(estado -> rechazarBorrador.ejecutar(id, cuerpo.motivo())));
  }

  /**
   * El primer paso para subir una foto al borrador: la URL firmada para que el navegador la suba
   * directo al bucket privado.
   */
  @PostMapping("/{id}/fotos/url-subida")
  public SubidaDeFotoRespuesta urlDeSubidaDeFoto(
      @PathVariable UUID id, @RequestBody SolicitarSubidaDeFotoPeticion cuerpo) {
    SolicitudDeSubida solicitud = solicitarSubidaDeFoto.ejecutar(id, cuerpo.contentType());
    return new SubidaDeFotoRespuesta(solicitud.url(), solicitud.objectKey());
  }

  /** El segundo: el servidor comprueba lo que se subió y lo cuelga del borrador. */
  @PostMapping("/{id}/fotos")
  @ResponseStatus(HttpStatus.CREATED)
  public BorradorDetalleRespuesta.FotoRespuesta confirmarFoto(
      @PathVariable UUID id, @RequestBody ConfirmarFotoPeticion cuerpo) {
    return BorradorDetalleRespuesta.FotoRespuesta.de(
        transaccion.execute(estado -> confirmarFoto.ejecutar(id, cuerpo.objectKey())));
  }

  /**
   * Saca una foto de la revisión. La del proveedor deja su archivo, porque es de la publicación; la
   * que se subió desde el panel se borra con el suyo.
   */
  @DeleteMapping("/{id}/fotos/{mensajeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void descartarFoto(@PathVariable UUID id, @PathVariable UUID mensajeId) {
    transaccion.executeWithoutResult(estado -> descartarFotoDeBorrador.ejecutar(id, mensajeId));
  }

  /**
   * Parte el borrador: las fotos nombradas se van a uno nuevo de la misma publicación, que es lo
   * que devuelve. Para cuando la lectura de fotos juntó dos productos en uno.
   */
  @PostMapping("/{id}/partir")
  @ResponseStatus(HttpStatus.CREATED)
  public BorradorRespuesta partir(
      @PathVariable UUID id, @RequestBody PartirBorradorPeticion cuerpo) {
    return BorradorRespuesta.de(
        transaccion.execute(estado -> partirBorrador.ejecutar(id, cuerpo.fotos())));
  }

  /**
   * {@code warn} con el conteo de fotos borradas del bucket, como al eliminar un producto: es la
   * única huella que queda de lo que había ahí.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable UUID id) {
    Integer objetos = transaccion.execute(estado -> eliminarBorrador.ejecutar(id));
    log.warn("Borrador eliminado: {} ({} fotos borradas del bucket)", id, objetos);
  }
}
