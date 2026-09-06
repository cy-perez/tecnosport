package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioSetsRotacionFalso implements RepositorioSetsRotacion {

  private final Map<UUID, SetRotacion> sets = new LinkedHashMap<>();
  SetRotacion ultimoGuardado;
  SetRotacion ultimoActualizado;
  UUID ultimoEliminado;

  void con(SetRotacion set) {
    sets.put(set.id(), set);
  }

  @Override
  public void guardar(SetRotacion set) {
    this.ultimoGuardado = set;
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
    this.ultimoActualizado = set;
    sets.put(set.id(), set);
  }

  @Override
  public void eliminar(UUID id) {
    this.ultimoEliminado = id;
    sets.remove(id);
  }
}
