package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import java.util.ArrayList;
import java.util.List;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioEnviosFalso implements RepositorioEnvios {

  private final List<Envio> envios = new ArrayList<>();

  @Override
  public void guardar(Envio envio) {
    envios.add(envio);
  }

  List<Envio> guardados() {
    return List.copyOf(envios);
  }
}
