package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Hermano del que vive en el
 * paquete {@code envio} y deliberadamente más tonto: aquí nadie emite, así que el único método que
 * hace algo es {@link #buscarDePedido}, que es lo que la cancelación recorre para anular.
 *
 * <p>El del paquete {@code envio} reproduce además el índice único que impide dos emisiones
 * abiertas. Copiarlo aquí sería copiar una regla que estas pruebas no ejercen, y una regla copiada
 * es una que puede quedar desincronizada sin que nada falle.
 */
final class RepositorioEmisionesFalso implements RepositorioEmisiones {

  private final Map<UUID, EmisionDeGuia> emisiones = new LinkedHashMap<>();

  @Override
  public void guardar(EmisionDeGuia emision) {
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
    return emisiones.values().stream().filter(e -> e.pedidoId().equals(pedidoId)).toList();
  }

  @Override
  public List<EmisionDeGuia> buscarEnCurso(int maximo) {
    return List.of();
  }

  @Override
  public List<EmisionDeGuia> buscarSolicitadasAntesDe(Instant corte, int maximo) {
    return List.of();
  }

  @Override
  public Optional<EmisionDeGuia> buscarPorId(UUID id) {
    return Optional.ofNullable(emisiones.get(id));
  }

  /**
   * Busca por el identificador que devolvió la plataforma, como el índice único de la base. Un
   * envío desconocido devuelve vacío, que es el caso del cobro de una guía tecleada a mano.
   */
  @Override
  public Optional<EmisionDeGuia> buscarPorEnvioEnPlataforma(String envioEnPlataforma) {
    return emisiones.values().stream()
        .filter(emision -> emision.enviosEnPlataforma().contains(envioEnPlataforma))
        .findFirst();
  }

  @Override
  public List<EmisionDeGuia> buscarQueExigenOjoHumano(int maximo) {
    return emisiones.values().stream()
        .filter(e -> e.estado().exigeOjoHumano())
        .limit(maximo)
        .toList();
  }
}
