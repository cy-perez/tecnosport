package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarMarcas;
import co.tecnosport.api.presentation.catalogo.dto.MarcaDeVitrinaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marcas")
public class MarcaControlador {

  private final ListarMarcas listarMarcas;
  private final MapeadorRespuestasCatalogo mapeador;

  public MarcaControlador(ListarMarcas listarMarcas, MapeadorRespuestasCatalogo mapeador) {
    this.listarMarcas = Objects.requireNonNull(listarMarcas);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  /**
   * Cada marca dice en qué líneas tiene algo publicado, y el filtro las usa para acotarse.
   *
   * <p>Viajan en la respuesta en vez de aceptar un {@code ?linea=}: son pocas marcas y cuatro
   * líneas, el navegador se trae la lista una vez por sesión, y así cambiar de línea no cuesta una
   * petición ni obliga al SSR a adivinar qué línea se va a pedir para precargarla.
   */
  @GetMapping
  public ResultadoPaginadoRespuesta<MarcaDeVitrinaRespuesta> listar() {
    return mapeador.aRespuestaDeMarcasDeVitrina(listarMarcas.ejecutar());
  }
}
