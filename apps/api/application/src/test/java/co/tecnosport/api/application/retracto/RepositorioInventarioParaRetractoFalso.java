package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioInventarioParaRetractoFalso implements RepositorioInventario {

  private final Map<UUID, Inventario> porVariante = new HashMap<>();

  void sembrar(Inventario inventario) {
    porVariante.put(inventario.varianteId(), inventario);
  }

  @Override
  public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
    return Optional.ofNullable(porVariante.get(varianteId));
  }

  @Override
  public void guardar(Inventario inventario) {
    porVariante.put(inventario.varianteId(), inventario);
  }

  /** No lo usa esta prueba: el listado de existencias tiene la suya. */
  @Override
  public List<Inventario> listarTodos() {
    return List.of();
  }
}
