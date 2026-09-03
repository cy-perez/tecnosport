package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.ListarCoberturaContraentrega;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público: el checkout la consulta para saber en qué ciudades ofrecer contraentrega. */
@RestController
@RequestMapping("/api/v1/envios/cobertura")
public class CoberturaContraentregaControlador {

  private final ListarCoberturaContraentrega listarCobertura;

  public CoberturaContraentregaControlador(ListarCoberturaContraentrega listarCobertura) {
    this.listarCobertura = Objects.requireNonNull(listarCobertura);
  }

  @GetMapping
  public List<String> listar() {
    return listarCobertura.ejecutar();
  }
}
