package co.tecnosport.api.application.difusion;

import co.tecnosport.api.domain.difusion.PublicacionEnRed;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las constancias de difusión. */
public interface RepositorioPublicaciones {

  void guardar(PublicacionEnRed publicacion);

  /**
   * La última difusión de un producto en una red, si la hay. La usa la ficha del panel para avisar
   * antes de repetir, y el caso de uso para su ventana de idempotencia.
   */
  Optional<PublicacionEnRed> ultimaDe(UUID productoId, RedSocial red);

  /** Todas las de un producto, de la más reciente a la más antigua. */
  List<PublicacionEnRed> historialDe(UUID productoId);

  /**
   * Si ya se pidió esa misma difusión hace nada.
   *
   * <p>Es la guarda del doble clic, y va en el repositorio y no en memoria porque el panel puede
   * estar abierto en dos pestañas o servido por dos instancias. La ventana es corta a propósito:
   * difundir el mismo producto en septiembre y otra vez en diciembre es el caso bueno, y una
   * restricción de unicidad lo habría prohibido para atajar un accidente de dos segundos.
   */
  boolean hayUnaReciente(UUID productoId, RedSocial red, Instant desde);
}
