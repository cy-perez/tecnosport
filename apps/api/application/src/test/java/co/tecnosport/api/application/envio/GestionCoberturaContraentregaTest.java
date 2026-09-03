package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Los tres casos de uso de cobertura son envoltorios delgados sobre el mismo puerto. */
class GestionCoberturaContraentregaTest {

  @Test
  void agregarQuedaVisibleAlListar() {
    RepositorioCoberturaContraentregaFalso repositorio =
        new RepositorioCoberturaContraentregaFalso();
    new AgregarCoberturaContraentrega(repositorio)
        .ejecutar(new AgregarCoberturaContraentregaComando("05001"));

    List<String> ciudades = new ListarCoberturaContraentrega(repositorio).ejecutar();

    assertTrue(ciudades.contains("05001"));
  }

  @Test
  void quitarLaExcluyeDelListado() {
    RepositorioCoberturaContraentregaFalso repositorio =
        new RepositorioCoberturaContraentregaFalso().conCiudadCubierta("05001");
    new QuitarCoberturaContraentrega(repositorio)
        .ejecutar(new QuitarCoberturaContraentregaComando("05001"));

    List<String> ciudades = new ListarCoberturaContraentrega(repositorio).ejecutar();

    assertTrue(ciudades.isEmpty());
  }

  @Test
  void agregarConCodigoVacioSeRechaza() {
    assertThrows(
        IllegalArgumentException.class, () -> new AgregarCoberturaContraentregaComando(" "));
  }

  @Test
  void quitarConCodigoVacioSeRechaza() {
    assertThrows(IllegalArgumentException.class, () -> new QuitarCoberturaContraentregaComando(""));
  }
}
