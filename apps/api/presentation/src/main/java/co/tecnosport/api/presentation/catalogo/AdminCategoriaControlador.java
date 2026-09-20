package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarCategoriasAdmin;
import co.tecnosport.api.presentation.catalogo.dto.CategoriaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las categorías para el formulario del panel, todas.
 *
 * <p>Mismo motivo que {@code AdminMarcaControlador}: {@code GET /api/v1/categorias} pasó a ser el
 * filtro de la vitrina. Aquí importa más, porque {@code V38} dejó la línea de tecnología con once
 * categorías y casi todas siguen vacías — sin este endpoint no habría forma de cargar el primer
 * proyector.
 *
 * <p>Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}.
 */
@RestController
@RequestMapping("/api/v1/admin/categorias")
public class AdminCategoriaControlador {

  private final ListarCategoriasAdmin listarCategoriasAdmin;
  private final MapeadorRespuestasCatalogo mapeador;

  public AdminCategoriaControlador(
      ListarCategoriasAdmin listarCategoriasAdmin, MapeadorRespuestasCatalogo mapeador) {
    this.listarCategoriasAdmin = Objects.requireNonNull(listarCategoriasAdmin);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<CategoriaRespuesta> listar() {
    return mapeador.aRespuestaDeCategorias(listarCategoriasAdmin.ejecutar());
  }
}
