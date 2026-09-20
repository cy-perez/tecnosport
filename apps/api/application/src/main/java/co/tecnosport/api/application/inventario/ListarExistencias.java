package co.tecnosport.api.application.inventario;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.inventario.Inventario;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lo que hay que ver para poder contar: cada variante activa con lo que dice el catálogo, lo que
 * dice el libro y lo que queda disponible.
 *
 * <p><b>Los saldos los calcula el dominio, no un {@code select}.</b> Traducir a SQL qué reserva
 * sigue vigente —no vencida, no resuelta por una salida, no resuelta por una liberación— sería
 * duplicar tres condiciones de {@link Inventario} que después tendrían que quedarse en sincronía
 * para siempre, en un sitio donde ninguna prueba de dominio las mira. El precio de hacerlo así está
 * escrito en adr/0049: esta consulta trae el histórico de movimientos completo, que crece con las
 * ventas y no solo con el tamaño del catálogo.
 *
 * <p><b>El orden lo decide este caso de uso</b>, como en {@code ListarVariantesSinMedir}: primero
 * las descuadradas, que son las que hay que mirar hoy; dentro de ellas los productos publicados,
 * que son los que le están mintiendo a alguien ahora mismo; y después por nombre y SKU, para que la
 * lista no baile entre dos cargas.
 */
public final class ListarExistencias {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public ListarExistencias(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ExistenciasDelCatalogo ejecutar() {
    Instant ahora = reloj.ahora();
    Map<UUID, Inventario> librosPorVariante =
        repositorioInventario.listarTodos().stream()
            .collect(
                Collectors.toMap(
                    Inventario::varianteId, Function.identity(), (primero, segundo) -> primero));

    return new ExistenciasDelCatalogo(
        repositorioProductos.variantesActivas().stream()
            .map(
                variante ->
                    aExistencia(variante, librosPorVariante.get(variante.varianteId()), ahora))
            .sorted(
                Comparator.comparing(ExistenciaDeVariante::descuadrada)
                    .reversed()
                    .thenComparing(ExistenciaDeVariante::estadoProducto, Comparator.reverseOrder())
                    .thenComparing(ExistenciaDeVariante::nombreProducto)
                    .thenComparing(ExistenciaDeVariante::sku))
            .toList());
  }

  /**
   * Una variante sin libro cuenta como saldo cero, no se salta. Si el catálogo declara existencia y
   * no hay un solo movimiento que la respalde, esa fila es precisamente la que alguien tiene que
   * ver: sale descuadrada, que es lo que es.
   */
  private ExistenciaDeVariante aExistencia(
      VarianteActiva variante, Inventario libro, Instant ahora) {
    int saldoTotal = libro == null ? 0 : libro.saldoTotal();
    int disponible = libro == null ? 0 : libro.saldoDisponible(ahora);
    return new ExistenciaDeVariante(
        variante.varianteId(),
        variante.productoId(),
        variante.nombreProducto(),
        variante.sku(),
        variante.estadoProducto(),
        variante.existenciaDeclarada(),
        saldoTotal,
        disponible);
  }
}
