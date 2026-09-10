package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de solicitudes de retracto. Implementación de producción: JPA, en infrastructure. */
public interface RepositorioSolicitudesRetracto {

  Optional<SolicitudRetracto> buscarPorId(UUID id);

  /**
   * Todas las del pedido, incluidas las rechazadas. Devuelve la lista y no solo la activa porque el
   * panel necesita ver el histórico: un retracto rechazado y vuelto a radicar es una historia que
   * hay que poder contar.
   */
  List<SolicitudRetracto> buscarPorPedidoId(UUID pedidoId);

  void guardar(SolicitudRetracto solicitud);
}
