package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.Envio;
import java.time.Instant;
import java.util.List;
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

  /**
   * Los envíos que llevan callados desde antes del corte: despachados hace rato y sin ningún evento
   * recibido después. Son los que la conciliación va a preguntarle a la transportadora (adr/0022).
   *
   * <p>Los que ya terminaron quedan fuera: un envío entregado, devuelto, cancelado o destruido no
   * tiene más historia que contar, y seguir preguntando por él gastaría cuota de un proveedor
   * limitado a dos peticiones por segundo.
   */
  List<Envio> buscarSinEventosDesde(Instant corte);
}
