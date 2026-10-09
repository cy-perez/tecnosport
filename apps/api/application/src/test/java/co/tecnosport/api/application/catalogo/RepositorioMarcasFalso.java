package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  private List<MarcaConLineas> conProductos = List.of();
  private final java.util.Map<UUID, Long> productosPorMarca = new java.util.HashMap<>();

  /** Productos en cualquier estado: lo que {@code contarProductos} responde. */
  void conProductosEn(Marca marca, long cuantos) {
    productosPorMarca.put(marca.id(), cuantos);
  }

  void conMarcas(Marca... marcas) {
    this.todas.clear();
    this.todas.addAll(List.of(marcas));
  }

  /** Con todas en la misma línea: a la mayoría de las pruebas la línea les da igual. */
  void conMarcasConProductosPublicados(Marca... marcas) {
    this.conProductos =
        Arrays.stream(marcas)
            .map(marca -> new MarcaConLineas(marca, Set.of(LineaCatalogo.TECNOLOGIA)))
            .toList();
  }

  void conMarcaEnLineas(Marca marca, LineaCatalogo... lineas) {
    List<MarcaConLineas> nuevas = new ArrayList<>(conProductos);
    nuevas.add(new MarcaConLineas(marca, Set.of(lineas)));
    this.conProductos = List.copyOf(nuevas);
  }

  List<Marca> guardadas() {
    return List.copyOf(todas);
  }

  @Override
  public List<Marca> listarTodas() {
    return List.copyOf(todas);
  }

  @Override
  public List<MarcaConLineas> listarConProductosPublicados() {
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

  @Override
  public boolean existeOtraConNombre(String nombre, UUID excepto) {
    return todas.stream()
        .anyMatch(marca -> !marca.id().equals(excepto) && marca.nombre().equalsIgnoreCase(nombre));
  }

  @Override
  public void actualizar(Marca marca) {
    todas.replaceAll(m -> m.id().equals(marca.id()) ? marca : m);
  }

  @Override
  public long contarProductos(UUID marcaId) {
    return productosPorMarca.getOrDefault(marcaId, 0L);
  }

  @Override
  public void eliminar(Marca marca) {
    todas.removeIf(m -> m.id().equals(marca.id()));
  }
}
