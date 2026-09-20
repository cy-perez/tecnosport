package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Las dos listas se guardan por separado <b>a propósito</b>, y esa es toda la gracia del doble:
 * si {@code listarConProductosPublicados()} devolviera lo mismo que {@code listarTodas()}, una
 * prueba que confundiera los dos casos de uso pasaría igual y no protegería de nada. Ya hubo un
 * doble en este proyecto cuyo reclamo atómico era un {@code Set.add()} y fijaba el defecto en
 * verde.
 */
final class RepositorioMarcasFalso implements RepositorioMarcas {

  private List<Marca> todas = List.of();
  private List<Marca> conProductos = List.of();

  void conMarcas(Marca... marcas) {
    this.todas = List.of(marcas);
  }

  void conMarcasConProductosPublicados(Marca... marcas) {
    this.conProductos = List.of(marcas);
  }

  @Override
  public List<Marca> listarTodas() {
    return todas;
  }

  @Override
  public List<Marca> listarConProductosPublicados() {
    return conProductos;
  }

  @Override
  public Optional<Marca> buscarPorId(UUID id) {
    return todas.stream().filter(marca -> marca.id().equals(id)).findFirst();
  }
}
