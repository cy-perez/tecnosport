package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioEnviosFalso implements RepositorioEnvios {

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

  @Override
  public Optional<Envio> buscarPorGuia(String guia) {
    return envios.stream().filter(e -> e.guia().equals(guia)).findFirst();
  }

  List<Envio> guardados() {
    return List.copyOf(envios);
  }

  /** Mismo criterio que la consulta real: callado desde el corte y sin evento terminal. */
  @Override
  public List<Envio> buscarSinEventosDesde(Instant corte, int maximo) {
    return envios.stream()
        .filter(e -> e.despachadoEn().isBefore(corte))
        .filter(e -> e.eventos().stream().noneMatch(ev -> !ev.recibidoEn().isBefore(corte)))
        .filter(e -> e.eventos().stream().noneMatch(ev -> ev.estado().esTerminal()))
        .limit(maximo)
        .toList();
  }
}
