package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.ArrayList;
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
 *
 * <p>{@code existeConNombre} compara sin distinguir mayúsculas porque así lo hace el índice de la
 * base ({@code V56}). Un doble que comparara exacto dejaría pasar en verde justo el caso que el
 * caso de uso existe para atrapar.
 */
final class RepositorioMarcasFalso implements RepositorioMarcas {

  private final List<Marca> todas = new ArrayList<>();
  private List<Marca> conProductos = List.of();

  void conMarcas(Marca... marcas) {
    this.todas.clear();
    this.todas.addAll(List.of(marcas));
  }

  void conMarcasConProductosPublicados(Marca... marcas) {
    this.conProductos = List.of(marcas);
  }

  List<Marca> guardadas() {
    return List.copyOf(todas);
  }

  @Override
  public List<Marca> listarTodas() {
    return List.copyOf(todas);
  }

  @Override
  public List<Marca> listarConProductosPublicados() {
    return conProductos;
  }

  @Override
  public Optional<Marca> buscarPorId(UUID id) {
    return todas.stream().filter(marca -> marca.id().equals(id)).findFirst();
  }

  @Override
  public boolean existeConNombre(String nombre) {
    return todas.stream().anyMatch(marca -> marca.nombre().equalsIgnoreCase(nombre));
  }

  @Override
  public void guardar(Marca marca) {
    todas.add(marca);
  }
}
