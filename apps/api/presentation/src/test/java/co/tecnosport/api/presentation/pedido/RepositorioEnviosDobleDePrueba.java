package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
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
    return envios.stream().filter(e -> e.guiaDe(guia).isPresent()).findFirst();
  }

  /** Mismo criterio que la consulta real: callado desde el corte y sin evento terminal. */
  @Override
  public List<Envio> buscarSinEventosDesde(Instant corte, int maximo) {
    return envios.stream()
        .filter(e -> e.despachadoEn().isBefore(corte))
        .filter(
            e ->
                e.guias().stream()
                    .anyMatch(
                        guia ->
                            guia.eventos().stream()
                                    .noneMatch(ev -> !ev.recibidoEn().isBefore(corte))
                                && !guia.terminada()))
        .limit(maximo)
        .toList();
  }

  /** Filtro grueso, igual que la consulta real: el ultimo estado de alguna guia pide ojo humano. */
  @Override
  public List<Envio> buscarConGuiasEnRevision(int maximo) {
    return envios.stream()
        .filter(
            e ->
                e.guias().stream()
                    .anyMatch(
                        guia ->
                            guia.ultimoEstado()
                                .filter(estado -> estado.exigeRevisionManual())
                                .isPresent()))
        .limit(maximo)
        .toList();
  }
}
