package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioSetsRotacion;
import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Es un bean singleton que
 * Spring reutiliza entre los métodos de la prueba, así que {@link #limpiar()} corre en cada
 * {@code @BeforeEach} — mismo motivo que en {@code RepositorioProductosDobleDePrueba}.
 */
class RepositorioSetsRotacionDobleDePrueba implements RepositorioSetsRotacion {

  private final Map<UUID, SetRotacion> sets = new LinkedHashMap<>();

  void con(SetRotacion set) {
    sets.put(set.id(), set);
  }

  void limpiar() {
    sets.clear();
  }

  @Override
  public void guardar(SetRotacion set) {
    sets.put(set.id(), set);
  }

  @Override
  public Optional<SetRotacion> buscarPorId(UUID id) {
    return Optional.ofNullable(sets.get(id));
  }

  @Override
  public Optional<SetRotacion> buscarPublicadoDeProducto(UUID productoId) {
    return sets.values().stream()
        .filter(set -> set.productoId().equals(productoId))
        .filter(set -> set.estado() == EstadoSetRotacion.PUBLICADO)
        .findFirst();
  }

  @Override
  public void actualizar(SetRotacion set) {
    sets.put(set.id(), set);
  }

  @Override
  public void eliminar(UUID id) {
    sets.remove(id);
  }
}
