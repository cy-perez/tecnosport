package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.AprobarBorrador;
import co.tecnosport.api.application.proveedores.AprobarBorradorComando;
import co.tecnosport.api.application.proveedores.EditarBorrador;
import co.tecnosport.api.application.proveedores.EditarBorradorComando;
import co.tecnosport.api.application.proveedores.EliminarBorrador;
import co.tecnosport.api.application.proveedores.RechazarBorrador;
import co.tecnosport.api.application.proveedores.RepositorioBorradores;
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
import co.tecnosport.api.presentation.proveedores.dto.BorradoresPaginadosRespuesta;
import co.tecnosport.api.presentation.proveedores.dto.EditarBorradorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.RechazarBorradorPeticion;
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
 * La bandeja de borradores: listar, ver, editar, aprobar, rechazar y borrar.
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

  private final RepositorioBorradores repositorioBorradores;
  private final VerBorrador verBorrador;
  private final EditarBorrador editarBorrador;
  private final AprobarBorrador aprobarBorrador;
  private final RechazarBorrador rechazarBorrador;
  private final EliminarBorrador eliminarBorrador;
  private final MapeadorRespuestasProductoAdmin mapeadorProducto;
  private final TransactionTemplate transaccion;

  public AdminBorradorControlador(
      RepositorioBorradores repositorioBorradores,
      VerBorrador verBorrador,
      EditarBorrador editarBorrador,
      AprobarBorrador aprobarBorrador,
      RechazarBorrador rechazarBorrador,
      EliminarBorrador eliminarBorrador,
      MapeadorRespuestasProductoAdmin mapeadorProducto,
      PlatformTransactionManager transactionManager) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.verBorrador = Objects.requireNonNull(verBorrador);
    this.editarBorrador = Objects.requireNonNull(editarBorrador);
    this.aprobarBorrador = Objects.requireNonNull(aprobarBorrador);
    this.rechazarBorrador = Objects.requireNonNull(rechazarBorrador);
    this.eliminarBorrador = Objects.requireNonNull(eliminarBorrador);
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
                        cuerpo.caracteristicas()))));
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
                                        f.mensajeId(), f.tono(), f.colorHex()))
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
