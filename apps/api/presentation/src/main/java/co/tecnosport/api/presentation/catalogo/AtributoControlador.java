package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarAtributos;
import co.tecnosport.api.presentation.catalogo.dto.AtributoRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dato de catálogo global, no sensible — el panel admin lo reutiliza igual que ya reutiliza {@code
 * /marcas} y {@code /categorias} para poblar el formulario de "agregar variante".
 */
@RestController
@RequestMapping("/api/v1/atributos")
public class AtributoControlador {

  private final ListarAtributos listarAtributos;
  private final MapeadorRespuestasCatalogo mapeador;

  public AtributoControlador(ListarAtributos listarAtributos, MapeadorRespuestasCatalogo mapeador) {
    this.listarAtributos = Objects.requireNonNull(listarAtributos);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @GetMapping
  public ResultadoPaginadoRespuesta<AtributoRespuesta> listar() {
    return mapeador.aRespuestaDeAtributos(listarAtributos.ejecutar());
  }
}
