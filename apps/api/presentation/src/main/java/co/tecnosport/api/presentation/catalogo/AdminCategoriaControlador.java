package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.CrearCategoria;
import co.tecnosport.api.application.catalogo.CrearCategoriaComando;
import co.tecnosport.api.application.catalogo.EditarCategoria;
import co.tecnosport.api.application.catalogo.EditarCategoriaComando;
import co.tecnosport.api.application.catalogo.EliminarCategoria;
import co.tecnosport.api.application.catalogo.ListarCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.presentation.catalogo.dto.CategoriaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.CrearCategoriaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.EditarCategoriaPeticion;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
 * El árbol de categorías, administrado.
 *
 * <p>El {@code GET} devuelve lo mismo que {@code GET /api/v1/categorias} desde que la vitrina dejó
 * de esconder las vacías, y se queda igualmente: el panel pide sus datos bajo {@code /admin/**} con
 * el token de admin, y hacerlo colgar del endpoint público lo ataría a una decisión de la vitrina
 * que ya cambió una vez.
 *
 * <p>Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap) — este controlador no repite esa regla, mismo criterio que {@code
 * AdminProductoControlador}.
 */
@RestController
@RequestMapping("/api/v1/admin/categorias")
public class AdminCategoriaControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminCategoriaControlador.class);

  private final ListarCategorias listarCategorias;
  private final CrearCategoria crearCategoria;
  private final EditarCategoria editarCategoria;
  private final EliminarCategoria eliminarCategoria;
  private final MapeadorRespuestasCatalogo mapeador;

  public AdminCategoriaControlador(
      ListarCategorias listarCategorias,
      CrearCategoria crearCategoria,
      EditarCategoria editarCategoria,
      EliminarCategoria eliminarCategoria,
      MapeadorRespuestasCatalogo mapeador) {
    this.listarCategorias = Objects.requireNonNull(listarCategorias);
    this.crearCategoria = Objects.requireNonNull(crearCategoria);
    this.editarCategoria = Objects.requireNonNull(editarCategoria);
    this.eliminarCategoria = Objects.requireNonNull(eliminarCategoria);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<CategoriaRespuesta> listar() {
    return mapeador.aRespuestaDeCategorias(listarCategorias.ejecutar());
  }

  /**
   * Las tres escrituras se registran porque una categoría la ve todo el sitio: sale en el menú, en
   * el filtro de la vitrina y en el desplegable de cada producto. Un árbol que cambió y nadie sabe
   * cuándo es un árbol que no se puede depurar.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoriaRespuesta crear(@RequestBody CrearCategoriaPeticion cuerpo) {
    Categoria categoria =
        crearCategoria.ejecutar(
            new CrearCategoriaComando(
                cuerpo.nombre(), cuerpo.slug(), linea(cuerpo.linea()), cuerpo.padreId()));
    log.info("Categoría creada: {} ({})", categoria.slug().valor(), categoria.id());
    return mapeador.aRespuesta(categoria);
  }

  @PutMapping("/{id}")
  public CategoriaRespuesta editar(
      @PathVariable UUID id, @RequestBody EditarCategoriaPeticion cuerpo) {
    Categoria categoria =
        editarCategoria.ejecutar(
            new EditarCategoriaComando(
                id, cuerpo.nombre(), cuerpo.slug(), linea(cuerpo.linea()), cuerpo.padreId()));
    log.info("Categoría editada: {} ({})", categoria.slug().valor(), categoria.id());
    return mapeador.aRespuesta(categoria);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable UUID id) {
    eliminarCategoria.ejecutar(id);
    log.info("Categoría eliminada: {}", id);
  }

  /**
   * La línea llega como texto porque el cuerpo es JSON y el panel manda la cadena del enum. Se
   * convierte aquí y no con el binder de Spring para poder distinguir "no la mandaron" —legítimo
   * cuando hay padre— de "mandaron una que no existe", que es un {@code 400} y no un {@code 500}.
   */
  private static LineaCatalogo linea(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    try {
      return LineaCatalogo.valueOf(valor.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Línea de catálogo desconocida: '" + valor + "'.", e);
    }
  }
}
