package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import java.time.Instant;
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
    // Como la base: si el pedido ya tiene otra abierta, esta no entra. Es lo que de verdad impide
    // el doble cobro, así que el doble tiene que hacerlo o las pruebas comprobarían otra cosa.
    emisiones.values().stream()
        .filter(otra -> !otra.id().equals(emision.id()))
        .filter(otra -> otra.pedidoId().equals(emision.pedidoId()))
        .filter(otra -> otra.estado().abierta())
        .findFirst()
        .ifPresent(
            abierta -> {
              if (emision.estado().abierta()) {
                throw new EmisionYaEnCursoException(
                    emision.pedidoId(), abierta.id(), abierta.estado());
              }
            });
    emisiones.put(emision.id(), emision);
  }

  @Override
  public Optional<EmisionDeGuia> buscarAbiertaDePedido(UUID pedidoId) {
    return emisiones.values().stream()
        .filter(emision -> emision.pedidoId().equals(pedidoId))
        .filter(emision -> emision.estado().abierta())
        .findFirst();
  }

  @Override
  public List<EmisionDeGuia> buscarDePedido(UUID pedidoId) {
    return emisiones.values().stream()
        .filter(emision -> emision.pedidoId().equals(pedidoId))
        .sorted(Comparator.comparing(EmisionDeGuia::solicitadaEn))
        .toList();
  }

  @Override
  public List<EmisionDeGuia> buscarEnCurso(int maximo) {
    return porEstado(EstadoEmision.EN_CURSO).limit(maximo).toList();
  }

  @Override
  public List<EmisionDeGuia> buscarSolicitadasAntesDe(Instant corte, int maximo) {
    return porEstado(EstadoEmision.SOLICITADA)
        .filter(emision -> emision.solicitadaEn().isBefore(corte))
        .limit(maximo)
        .toList();
  }

  @Override
  public Optional<EmisionDeGuia> buscarPorId(UUID id) {
    return Optional.ofNullable(emisiones.get(id));
  }

  @Override
  public List<EmisionDeGuia> buscarQueExigenOjoHumano(int maximo) {
    return emisiones.values().stream()
        .filter(emision -> emision.estado().exigeOjoHumano())
        .sorted(Comparator.comparing(EmisionDeGuia::solicitadaEn))
        .limit(maximo)
        .toList();
  }

  private java.util.stream.Stream<EmisionDeGuia> porEstado(EstadoEmision estado) {
    return emisiones.values().stream()
        .filter(emision -> emision.estado() == estado)
        .sorted(Comparator.comparing(EmisionDeGuia::solicitadaEn));
  }

  List<EmisionDeGuia> todas() {
    return List.copyOf(emisiones.values());
  }
}
