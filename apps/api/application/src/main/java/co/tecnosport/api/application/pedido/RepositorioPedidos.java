package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPedidos {

  Optional<Pedido> buscarPorId(UUID id);

  void guardar(Pedido pedido);

  /**
   * Reserva de forma atómica el siguiente secuencial del número legible para {@code anio}
   * (apps/api/CLAUDE.md: {@code TS-2026-000123}). Cada año arranca su propio contador en 1.
   */
  NumeroPedido siguienteNumero(int anio);
}
