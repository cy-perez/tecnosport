package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.HashMap;
import java.util.Map;
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
}
