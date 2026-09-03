package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPagos {

  Optional<Pago> buscarPorReferencia(ReferenciaPago referencia);

  /** Todos los intentos de pago de un pedido, para numerar el siguiente tras un reintento. */
  List<Pago> buscarPorPedidoId(UUID pedidoId);

  /**
   * {@code PENDIENTE}, con {@code idTransaccionWompi} registrado, creados antes de {@code
   * creadosAntesDe} — el universo de la conciliación programada (docs/11-pagos-y-envios.md). Un
   * pago sin ese id no aparece aquí: no hay cómo consultarlo en la API de Wompi.
   */
  List<Pago> buscarPendientesParaConciliar(Instant creadosAntesDe);

  void guardar(Pago pago);
}
