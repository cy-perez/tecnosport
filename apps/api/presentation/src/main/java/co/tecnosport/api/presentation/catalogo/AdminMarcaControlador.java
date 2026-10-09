package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.CrearMarca;
import co.tecnosport.api.application.catalogo.EliminarMarca;
import co.tecnosport.api.application.catalogo.ListarMarcasAdmin;
import co.tecnosport.api.application.catalogo.RenombrarMarca;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.presentation.catalogo.dto.CrearMarcaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.EditarMarcaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.MarcaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Las marcas para el formulario del panel, todas.
 *
 * <p>Existe porque {@code GET /api/v1/marcas} dejó de devolverlas todas: ese endpoint alimenta el
 * filtro de la vitrina y ahora solo ofrece las que tienen algo publicado detrás. El panel necesita
 * lo contrario — la marca sin productos es precisamente la que hace falta para cargarle el primero.
 *
 * <p>Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap) — este controlador no repite esa regla, mismo criterio que {@code
 * AdminProductoControlador}.
 */
@RestController
@RequestMapping("/api/v1/admin/marcas")
public class AdminMarcaControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminMarcaControlador.class);

  private final ListarMarcasAdmin listarMarcasAdmin;
  private final CrearMarca crearMarca;
  private final RenombrarMarca renombrarMarca;
  private final EliminarMarca eliminarMarca;
  private final MapeadorRespuestasCatalogo mapeador;
  private final TransactionTemplate transaccion;

  public AdminMarcaControlador(
      ListarMarcasAdmin listarMarcasAdmin,
      CrearMarca crearMarca,
      RenombrarMarca renombrarMarca,
      EliminarMarca eliminarMarca,
      MapeadorRespuestasCatalogo mapeador,
      PlatformTransactionManager transactionManager) {
    this.listarMarcasAdmin = Objects.requireNonNull(listarMarcasAdmin);
    this.crearMarca = Objects.requireNonNull(crearMarca);
    this.renombrarMarca = Objects.requireNonNull(renombrarMarca);
    this.eliminarMarca = Objects.requireNonNull(eliminarMarca);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<MarcaRespuesta> listar() {
    return mapeador.aRespuestaDeMarcas(listarMarcasAdmin.ejecutar());
  }

  /**
   * Se registra el alta porque una marca nueva la ve todo el catálogo: aparece en el desplegable de
   * cada producto y, en cuanto tenga uno publicado, en el filtro de la vitrina.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MarcaRespuesta crear(@RequestBody CrearMarcaPeticion cuerpo) {
    Marca marca = crearMarca.ejecutar(cuerpo.nombre());
    log.info("Marca creada: {} ({})", marca.nombre(), marca.id());
    return new MarcaRespuesta(marca.id(), marca.nombre());
  }

  /**
   * Se registra por lo mismo que el alta: el nombre nuevo lo ve el comprador en cada ficha de la
   * marca y en el filtro. {@code 409} si otra marca ya se llama así, sin distinguir mayúsculas.
   */
  @PutMapping("/{id}")
  public MarcaRespuesta editar(@PathVariable UUID id, @RequestBody EditarMarcaPeticion cuerpo) {
    Marca marca = transaccion.execute(estado -> renombrarMarca.ejecutar(id, cuerpo.nombre()));
    log.info("Marca renombrada: {} ({})", marca.nombre(), marca.id());
    return new MarcaRespuesta(marca.id(), marca.nombre());
  }

  /** {@code 204}; {@code 409} si tiene productos, con cuántos en el detalle. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable UUID id) {
    transaccion.executeWithoutResult(estado -> eliminarMarca.ejecutar(id));
    log.info("Marca eliminada: {}", id);
  }
}
