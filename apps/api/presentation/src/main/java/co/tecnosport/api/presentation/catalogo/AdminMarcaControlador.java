package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarMarcasAdmin;
import co.tecnosport.api.presentation.catalogo.dto.MarcaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

  private final ListarMarcasAdmin listarMarcasAdmin;
  private final MapeadorRespuestasCatalogo mapeador;

  public AdminMarcaControlador(
      ListarMarcasAdmin listarMarcasAdmin, MapeadorRespuestasCatalogo mapeador) {
    this.listarMarcasAdmin = Objects.requireNonNull(listarMarcasAdmin);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<MarcaRespuesta> listar() {
    return mapeador.aRespuestaDeMarcas(listarMarcasAdmin.ejecutar());
  }
}
