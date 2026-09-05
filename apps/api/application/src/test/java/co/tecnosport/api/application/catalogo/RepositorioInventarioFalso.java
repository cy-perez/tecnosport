package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioInventarioFalso implements RepositorioInventario {

  private final Map<UUID, Inventario> porVarianteId = new HashMap<>();
  Inventario ultimoGuardado;

  @Override
  public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
    return Optional.ofNullable(porVarianteId.get(varianteId));
  }

  @Override
  public void guardar(Inventario inventario) {
    this.ultimoGuardado = inventario;
    porVarianteId.put(inventario.varianteId(), inventario);
  }
}
