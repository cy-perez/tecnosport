package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class RepositorioInventarioParaRetractoFalso implements RepositorioInventario {

  private final Map<UUID, Inventario> porVariante = new HashMap<>();

  void sembrar(Inventario inventario) {
    porVariante.put(inventario.varianteId(), inventario);
  }

  @Override
  public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
    return Optional.ofNullable(porVariante.get(varianteId))
        .map(RepositorioInventarioParaRetractoFalso::reconstituido);
  }

  @Override
  public void guardar(Inventario inventario) {
    porVariante.put(inventario.varianteId(), reconstituido(inventario));
  }

  /** No lo usa esta prueba: el listado de existencias tiene la suya. */
  @Override
  public List<Inventario> listarTodos() {
    return List.of();
  }

  @Override
  public List<Inventario> buscarPorVarianteIds(Collection<UUID> varianteIds) {
    return varianteIds.stream()
        .map(porVariante::get)
        .filter(Objects::nonNull)
        .map(RepositorioInventarioParaRetractoFalso::reconstituido)
        .toList();
  }

  /**
   * Cada lectura devuelve un agregado nuevo, igual que {@code RepositorioInventarioJpa} al
   * reconstruirlo desde sus filas. Devolver la instancia guardada hacía que una mutación sin {@code
   * guardar} se viera igual que una guardada, y eso en producción es sobreventa.
   */
  private static Inventario reconstituido(Inventario inventario) {
    return new Inventario(inventario.id(), inventario.varianteId(), inventario.movimientos());
  }
}
