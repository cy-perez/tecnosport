package co.tecnosport.api.application.legal;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Público porque lo comparten
 * las pruebas del registro y las de la creación de pedido, los dos puntos donde se recoge la
 * autorización.
 */
public final class RepositorioAutorizacionesFalso implements RepositorioAutorizaciones {

  private final List<AutorizacionDatos> autorizaciones = new ArrayList<>();

  @Override
  public void guardar(AutorizacionDatos autorizacion) {
    autorizaciones.add(autorizacion);
  }

  @Override
  public List<AutorizacionDatos> buscarPorCorreo(CorreoElectronico correo) {
    return autorizaciones.stream()
        .filter(a -> a.correo().equals(correo))
        .sorted(Comparator.comparing(AutorizacionDatos::otorgadaEn).reversed())
        .toList();
  }

  public List<AutorizacionDatos> todas() {
    return List.copyOf(autorizaciones);
  }
}
