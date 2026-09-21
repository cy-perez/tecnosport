package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioInventarioFalso implements RepositorioInventario {

  private final Map<UUID, Inventario> porVarianteId = new HashMap<>();
  private int consultasConBloqueo;

  void conInventario(Inventario inventario) {
    porVarianteId.put(inventario.varianteId(), inventario);
  }

  @Override
  public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
    consultasConBloqueo++;
    return Optional.ofNullable(porVarianteId.get(varianteId))
        .map(RepositorioInventarioFalso::reconstituido);
  }

  /**
   * Cuántas veces se pidió un inventario. En producción cada una toma un bloqueo pesimista, así que
   * este contador es lo que permite comprobar que nadie sostenga uno mientras habla con un tercero.
   */
  int consultasConBloqueo() {
    return consultasConBloqueo;
  }

  @Override
  public void guardar(Inventario inventario) {
    porVarianteId.put(inventario.varianteId(), reconstituido(inventario));
  }

  /** No lo usa esta prueba: el listado de existencias tiene la suya. */
  @Override
  public List<Inventario> listarTodos() {
    return List.of();
  }

  @Override
  public List<Inventario> buscarPorVarianteIds(Collection<UUID> varianteIds) {
    return varianteIds.stream()
        .map(porVarianteId::get)
        .filter(Objects::nonNull)
        .map(RepositorioInventarioFalso::reconstituido)
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
