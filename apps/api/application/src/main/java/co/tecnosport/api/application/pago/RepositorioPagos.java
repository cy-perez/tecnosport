package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPagos {

  Optional<Pago> buscarPorReferencia(ReferenciaPago referencia);

  /** Todos los intentos de pago de un pedido, para numerar el siguiente tras un reintento. */
  List<Pago> buscarPorPedidoId(UUID pedidoId);

  void guardar(Pago pago);
}
