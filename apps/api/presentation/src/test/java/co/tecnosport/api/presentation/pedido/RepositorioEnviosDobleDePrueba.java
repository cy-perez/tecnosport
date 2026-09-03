package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import java.util.ArrayList;
import java.util.List;

final class RepositorioEnviosDobleDePrueba implements RepositorioEnvios {

  private final List<Envio> envios = new ArrayList<>();

  @Override
  public void guardar(Envio envio) {
    envios.add(envio);
  }

  List<Envio> guardados() {
    return List.copyOf(envios);
  }
}
