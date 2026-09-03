package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.Envio;

public interface RepositorioEnvios {

  void guardar(Envio envio);
}
