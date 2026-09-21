package co.tecnosport.api.presentation.retracto;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class RepositorioInventarioDobleDePrueba implements RepositorioInventario {

  private final Map<UUID, Inventario> porVarianteId = new HashMap<>();

  void conInventario(Inventario inventario) {
    porVarianteId.put(inventario.varianteId(), inventario);
  }

  @Override
  public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
    return Optional.ofNullable(porVarianteId.get(varianteId));
  }

  @Override
  public void guardar(Inventario inventario) {
    porVarianteId.put(inventario.varianteId(), inventario);
  }

  void limpiar() {
    porVarianteId.clear();
  }

  /** No lo usa esta prueba: el listado de existencias tiene la suya. */
  @Override
  public List<Inventario> listarTodos() {
    return List.of();
  }

  @Override
  public List<Inventario> buscarPorVarianteIds(Collection<UUID> varianteIds) {
    return varianteIds.stream().map(porVarianteId::get).filter(Objects::nonNull).toList();
  }
}
