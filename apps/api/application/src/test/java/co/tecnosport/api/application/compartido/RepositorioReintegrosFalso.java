package co.tecnosport.api.application.compartido;

import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble escrito a mano, sin Mockito (docs/06-testing.md). Público y en {@code compartido} por lo
 * mismo que {@link RelojFalso} y {@link TextosDeCorreoFalso}: lo necesitan cuatro paquetes de
 * prueba.
 *
 * <p>Antes había cuatro copias idénticas —una en {@code garantia}, {@code pedido}, {@code retracto}
 * y {@code reversion}, byte a byte iguales salvo la línea del paquete— y la prueba del tope
 * acumulado estuvo a punto de ser la quinta: se anidó dentro del propio test con un comentario que
 * decía "no hacen falta cinco", que es exactamente lo que era. Lo levantó una revisión adversarial,
 * y con razón: la solución ya estaba en la misma rama, dos archivos más allá.
 */
public final class RepositorioReintegrosFalso implements RepositorioReintegros {

  private final List<Reintegro> guardados = new ArrayList<>();

  public List<Reintegro> guardados() {
    return List.copyOf(guardados);
  }

  @Override
  public void guardar(Reintegro reintegro) {
    guardados.removeIf(r -> r.id().equals(reintegro.id()));
    guardados.add(reintegro);
  }

  @Override
  public Optional<Reintegro> buscarPorId(UUID id) {
    return guardados.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  @Override
  public List<Reintegro> buscarPorPedido(UUID pedidoId) {
    return guardados.stream().filter(r -> r.pedidoId().equals(pedidoId)).toList();
  }

  @Override
  public Optional<Reintegro> buscarPorOrigen(UUID origenId) {
    return guardados.stream().filter(r -> r.origenId().equals(origenId)).findFirst();
  }
}
