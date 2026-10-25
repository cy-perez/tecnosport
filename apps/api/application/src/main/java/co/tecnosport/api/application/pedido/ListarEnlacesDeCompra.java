package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** La bandeja de enlaces de compra del panel: los de la última semana, con su estado. */
public final class ListarEnlacesDeCompra {

  static final Duration VENTANA = Duration.ofDays(7);

  private final RepositorioEnlacesDeCompra repositorioEnlaces;
  private final RepositorioProductos repositorioProductos;
  private final Reloj reloj;

  public ListarEnlacesDeCompra(
      RepositorioEnlacesDeCompra repositorioEnlaces,
      RepositorioProductos repositorioProductos,
      Reloj reloj) {
    this.repositorioEnlaces = Objects.requireNonNull(repositorioEnlaces);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public List<Fila> ejecutar() {
    Instant ahora = reloj.ahora();
    List<Fila> filas = new ArrayList<>();
    for (EnlaceDeCompra enlace : repositorioEnlaces.listarCreadosDesde(ahora.minus(VENTANA))) {
      filas.add(fila(enlace, ahora));
    }
    return filas;
  }

  /**
   * La fila de un solo enlace, para responder a una acción sobre él: uno anulado puede ser de antes
   * de la ventana de la bandeja.
   */
  public Fila fila(EnlaceDeCompra enlace) {
    return fila(enlace, reloj.ahora());
  }

  private Fila fila(EnlaceDeCompra enlace, Instant ahora) {
    Optional<Producto> producto = repositorioProductos.buscarPorVarianteId(enlace.varianteId());
    Optional<Variante> variante =
        producto.flatMap(
            p ->
                p.variantes().stream().filter(v -> v.id().equals(enlace.varianteId())).findFirst());
    return new Fila(enlace, producto.orElse(null), variante.orElse(null), estado(enlace, ahora));
  }

  static Estado estado(EnlaceDeCompra enlace, Instant ahora) {
    if (enlace.usadoEn().isPresent()) {
      return Estado.USADO;
    }
    if (enlace.anuladoEn().isPresent()) {
      return Estado.ANULADO;
    }
    return enlace.vigente(ahora) ? Estado.VIGENTE : Estado.VENCIDO;
  }

  public enum Estado {
    VIGENTE,
    USADO,
    ANULADO,
    VENCIDO
  }

  /**
   * @param producto nulo si se borró después de generar el enlace
   * @param variante nula por lo mismo
   */
  public record Fila(EnlaceDeCompra enlace, Producto producto, Variante variante, Estado estado) {}
}
