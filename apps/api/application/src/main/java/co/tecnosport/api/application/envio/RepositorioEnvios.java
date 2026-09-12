package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.Envio;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioEnvios {

  void guardar(Envio envio);

  Optional<Envio> buscarPorPedidoId(UUID pedidoId);

  /**
   * Por número de guía, que es con lo que llegan los eventos de la transportadora.
   *
   * <p>Sin la transportadora: dos guías iguales de empresas distintas son posibles en teoría y
   * nadie las ha visto en la práctica. El día que aparezcan, el síntoma será un evento aplicado al
   * envío equivocado, y la corrección es pedir también la transportadora aquí.
   */
  Optional<Envio> buscarPorGuia(String guia);
}
