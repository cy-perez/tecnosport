package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.HashMap;
import java.util.Map;
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
    return Optional.ofNullable(porVarianteId.get(varianteId));
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
    porVarianteId.put(inventario.varianteId(), inventario);
  }
}
