package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioEmisionesFalso implements RepositorioEmisiones {

  private final Map<UUID, EmisionDeGuia> emisiones = new LinkedHashMap<>();

  @Override
  public void guardar(EmisionDeGuia emision) {
    emisiones.put(emision.id(), emision);
  }

  @Override
  public Optional<EmisionDeGuia> buscarEnCursoDePedido(UUID pedidoId) {
    return emisiones.values().stream()
        .filter(emision -> emision.pedidoId().equals(pedidoId))
        .filter(emision -> emision.estado().enCurso())
        .findFirst();
  }

  @Override
  public List<EmisionDeGuia> buscarEnCurso(int maximo) {
    return emisiones.values().stream()
        .filter(emision -> emision.estado().enCurso())
        .sorted(Comparator.comparing(EmisionDeGuia::solicitadaEn))
        .limit(maximo)
        .toList();
  }

  List<EmisionDeGuia> todas() {
    return List.copyOf(emisiones.values());
  }
}
