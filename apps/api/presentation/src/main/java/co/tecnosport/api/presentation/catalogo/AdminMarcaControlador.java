package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.CrearMarca;
import co.tecnosport.api.application.catalogo.ListarMarcasAdmin;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.presentation.catalogo.dto.CrearMarcaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.MarcaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final MapeadorRespuestasCatalogo mapeador;

  public AdminMarcaControlador(
      ListarMarcasAdmin listarMarcasAdmin,
      CrearMarca crearMarca,
      MapeadorRespuestasCatalogo mapeador) {
    this.listarMarcasAdmin = Objects.requireNonNull(listarMarcasAdmin);
    this.crearMarca = Objects.requireNonNull(crearMarca);
    this.mapeador = Objects.requireNonNull(mapeador);
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
}
