package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioEmisiones {

  void guardar(EmisionDeGuia emision);

  /**
   * La emisión abierta de un pedido, si la hay. Es lo que impide pedir dos veces las guías del
   * mismo pedido y que la plataforma cobre dos veces: la segunda solicitud se rechaza antes de
   * salir. Solo puede haber una, y lo garantiza además un índice único parcial en la base — sin él,
   * dos clics seguidos en el panel se cuelan por la ventana entre leer y escribir.
   */
  Optional<EmisionDeGuia> buscarEnCursoDePedido(UUID pedidoId);

  /**
   * Las emisiones que siguen esperando respuesta, para que la tarea programada las relea. Ordenadas
   * de la más vieja a la más nueva: si hay más de las que caben en un lote, la que lleva más rato
   * esperando es la que más urge.
   */
  List<EmisionDeGuia> buscarEnCurso(int maximo);
}
