package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarMarcas;
import co.tecnosport.api.presentation.catalogo.dto.MarcaRespuesta;
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

  @GetMapping
  public ResultadoPaginadoRespuesta<MarcaRespuesta> listar() {
    return mapeador.aRespuestaDeMarcas(listarMarcas.ejecutar());
  }
}
