package co.tecnosport.api.application.envio;

import java.util.Objects;
import java.util.UUID;

/**
 * Le quita el promedio a una categoría ({@code adr/0071}): sus variantes sin medir vuelven a
 * venderse solo con recogida, como en el {@code adr/0046}.
 *
 * <p>Quitar uno que no estaba no es un error: el estado final es el que se pidió.
 */
public final class QuitarPesoDeReferencia {

  private final RepositorioReferenciasDeEnvio referencias;

  public QuitarPesoDeReferencia(RepositorioReferenciasDeEnvio referencias) {
    this.referencias =
        Objects.requireNonNull(referencias, "El repositorio de referencias no puede ser nulo.");
  }

  public void ejecutar(UUID categoriaId) {
    Objects.requireNonNull(categoriaId, "La categoría no puede ser nula.");
    referencias.quitarPeso(categoriaId);
  }
}
