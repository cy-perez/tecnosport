package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioProveedoresFalso implements RepositorioProveedores {

  private final Map<UUID, Proveedor> porId = new LinkedHashMap<>();

  void conProveedores(Proveedor... proveedores) {
    for (Proveedor proveedor : proveedores) {
      porId.put(proveedor.id(), proveedor);
    }
  }

  @Override
  public void guardar(Proveedor proveedor) {
    porId.put(proveedor.id(), proveedor);
  }

  @Override
  public void actualizar(Proveedor proveedor) {
    porId.put(proveedor.id(), proveedor);
  }

  @Override
  public Optional<Proveedor> buscarPorId(UUID id) {
    return Optional.ofNullable(porId.get(id));
  }

  @Override
  public List<Proveedor> listar() {
    return new ArrayList<>(porId.values());
  }

  @Override
  public DependenciasDeProveedor dependenciasDe(UUID id) {
    throw new UnsupportedOperationException("Ninguna prueba de pedidos lo necesita.");
  }

  @Override
  public void eliminarConSuHistorial(UUID id) {
    porId.remove(id);
  }
}
