package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarCategorias;
import co.tecnosport.api.presentation.catalogo.dto.CategoriaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaControlador {

  private final ListarCategorias listarCategorias;
  private final MapeadorRespuestasCatalogo mapeador;

  public CategoriaControlador(
      ListarCategorias listarCategorias, MapeadorRespuestasCatalogo mapeador) {
    this.listarCategorias = Objects.requireNonNull(listarCategorias);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<CategoriaRespuesta> listar() {
    return mapeador.aRespuestaDeCategorias(listarCategorias.ejecutar());
  }
}
