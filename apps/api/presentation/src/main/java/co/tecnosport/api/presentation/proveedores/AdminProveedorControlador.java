package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.CrearProveedor;
import co.tecnosport.api.application.proveedores.CrearProveedorComando;
import co.tecnosport.api.application.proveedores.EditarProveedor;
import co.tecnosport.api.application.proveedores.EditarProveedorComando;
import co.tecnosport.api.application.proveedores.EliminarProveedor;
import co.tecnosport.api.application.proveedores.ProveedorNoEncontradoException;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.presentation.proveedores.dto.ProveedorPeticion;
import co.tecnosport.api.presentation.proveedores.dto.ProveedorRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta, edición, listado y eliminación de proveedores. La lectura va directo al repositorio, como
 * el historial de difusión.
 */
@RestController
@RequestMapping("/api/v1/admin/proveedores")
public class AdminProveedorControlador {

  private final CrearProveedor crearProveedor;
  private final EditarProveedor editarProveedor;
  private final EliminarProveedor eliminarProveedor;
  private final RepositorioProveedores repositorioProveedores;
  private final TransactionTemplate transaccion;

  public AdminProveedorControlador(
      CrearProveedor crearProveedor,
      EditarProveedor editarProveedor,
      EliminarProveedor eliminarProveedor,
      RepositorioProveedores repositorioProveedores,
      PlatformTransactionManager transactionManager) {
    this.crearProveedor = Objects.requireNonNull(crearProveedor);
    this.editarProveedor = Objects.requireNonNull(editarProveedor);
    this.eliminarProveedor = Objects.requireNonNull(eliminarProveedor);
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public List<ProveedorRespuesta> listar() {
    return repositorioProveedores.listar().stream().map(ProveedorRespuesta::de).toList();
  }

  @GetMapping("/{id}")
  public ProveedorRespuesta ver(@PathVariable UUID id) {
    return repositorioProveedores
        .buscarPorId(id)
        .map(ProveedorRespuesta::de)
        .orElseThrow(() -> new ProveedorNoEncontradoException(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProveedorRespuesta crear(@RequestBody ProveedorPeticion cuerpo) {
    Proveedor proveedor =
        transaccion.execute(
            estado ->
                crearProveedor.ejecutar(
                    new CrearProveedorComando(
                        cuerpo.nombre(),
                        LineaCatalogo.valueOf(cuerpo.linea()),
                        cuerpo.telefonoWhatsApp(),
                        cuerpo.nombreEnExportacion(),
                        cuerpo.factorDeMargen(),
                        OrdenDePublicacion.valueOf(cuerpo.ordenDePublicacion()))));
    return ProveedorRespuesta.de(proveedor);
  }

  /** Edición completa: lo que no venga en el cuerpo queda como estaba. */
  @PutMapping("/{id}")
  public ProveedorRespuesta editar(@PathVariable UUID id, @RequestBody ProveedorPeticion cuerpo) {
    Proveedor actual =
        repositorioProveedores
            .buscarPorId(id)
            .orElseThrow(() -> new ProveedorNoEncontradoException(id));
    Proveedor proveedor =
        transaccion.execute(
            estado ->
                editarProveedor.ejecutar(
                    new EditarProveedorComando(
                        id,
                        cuerpo.nombre(),
                        LineaCatalogo.valueOf(cuerpo.linea()),
                        cuerpo.telefonoWhatsApp(),
                        cuerpo.nombreEnExportacion(),
                        cuerpo.activo() != null ? cuerpo.activo() : actual.activo(),
                        cuerpo.publicacionAutomatica() != null
                            ? cuerpo.publicacionAutomatica()
                            : actual.publicacionAutomatica(),
                        cuerpo.factorDeMargen(),
                        OrdenDePublicacion.valueOf(cuerpo.ordenDePublicacion()))));
    return ProveedorRespuesta.de(proveedor);
  }

  /**
   * Con productos en el catálogo o con una ingesta abierta, {@code 409}: ver {@link
   * EliminarProveedor}.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable UUID id) {
    transaccion.executeWithoutResult(estado -> eliminarProveedor.ejecutar(id));
  }
}
