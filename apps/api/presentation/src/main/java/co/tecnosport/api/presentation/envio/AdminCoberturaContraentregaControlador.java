package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.AgregarCoberturaContraentrega;
import co.tecnosport.api.application.envio.AgregarCoberturaContraentregaComando;
import co.tecnosport.api.application.envio.QuitarCoberturaContraentrega;
import co.tecnosport.api.application.envio.QuitarCoberturaContraentregaComando;
import co.tecnosport.api.presentation.envio.dto.CoberturaContraentregaRequest;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sin UI todavía (docs/09-plan-de-arranque.md): así carga el administrador la cobertura real de la
 * transportadora con recaudo, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap).
 */
@RestController
@RequestMapping("/api/v1/admin/cobertura-contraentrega")
public class AdminCoberturaContraentregaControlador {

  private final AgregarCoberturaContraentrega agregarCobertura;
  private final QuitarCoberturaContraentrega quitarCobertura;

  public AdminCoberturaContraentregaControlador(
      AgregarCoberturaContraentrega agregarCobertura,
      QuitarCoberturaContraentrega quitarCobertura) {
    this.agregarCobertura = Objects.requireNonNull(agregarCobertura);
    this.quitarCobertura = Objects.requireNonNull(quitarCobertura);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void agregar(@RequestBody CoberturaContraentregaRequest cuerpo) {
    agregarCobertura.ejecutar(new AgregarCoberturaContraentregaComando(cuerpo.codigoDaneCiudad()));
  }

  @DeleteMapping("/{codigoDaneCiudad}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void quitar(@PathVariable String codigoDaneCiudad) {
    quitarCobertura.ejecutar(new QuitarCoberturaContraentregaComando(codigoDaneCiudad));
  }
}
