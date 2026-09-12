package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RepositorioEnviosDobleDePrueba implements RepositorioEnvios {

  private final List<Envio> envios = new ArrayList<>();

  @Override
  public void guardar(Envio envio) {
    envios.removeIf(e -> e.id().equals(envio.id()));
    envios.add(envio);
  }

  @Override
  public Optional<Envio> buscarPorPedidoId(UUID pedidoId) {
    return envios.stream().filter(e -> e.pedidoId().equals(pedidoId)).findFirst();
  }

  List<Envio> guardados() {
    return List.copyOf(envios);
  }

  @Override
  public Optional<Envio> buscarPorGuia(String guia) {
    return envios.stream().filter(e -> e.guia().equals(guia)).findFirst();
  }

  /** Mismo criterio que la consulta real: callado desde el corte y sin evento terminal. */
  @Override
  public List<Envio> buscarSinEventosDesde(Instant corte) {
    return envios.stream()
        .filter(e -> e.despachadoEn().isBefore(corte))
        .filter(e -> e.eventos().stream().noneMatch(ev -> !ev.recibidoEn().isBefore(corte)))
        .filter(
            e ->
                e.eventos().stream()
                    .noneMatch(
                        ev ->
                            ev.estado() == EstadoEnvio.ENTREGADO
                                || ev.estado() == EstadoEnvio.EN_DEVOLUCION
                                || ev.estado() == EstadoEnvio.CANCELADO
                                || ev.estado() == EstadoEnvio.DESTRUIDO))
        .toList();
  }
}
