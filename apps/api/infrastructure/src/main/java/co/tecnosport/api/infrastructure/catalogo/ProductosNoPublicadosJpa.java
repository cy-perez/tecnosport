package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.ProductosNoPublicados;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Ordena por id: a {@code uuid} Postgres le da un orden total, que es lo único que el cursor pide.
 * Ese orden <b>no es el de {@code UUID.compareTo}</b> —Java compara los dos {@code long} con
 * signo—, y no hace falta que lo sea: el orden, el {@code >} del cursor y el {@code <=} del tope
 * los resuelve la misma base.
 */
@Repository
public class ProductosNoPublicadosJpa implements ProductosNoPublicados {

  private static final String BORRADOR = EstadoProducto.BORRADOR.name();

  private final ProductoJpaRepository jpa;

  public ProductosNoPublicadosJpa(ProductoJpaRepository jpa) {
    this.jpa = Objects.requireNonNull(jpa);
  }

  @Override
  public long contar() {
    return jpa.countByEstado(BORRADOR);
  }

  @Override
  public Optional<UUID> ultimo() {
    return jpa.idsEnEstadoAlReves(BORRADOR, PageRequest.of(0, 1)).stream().findFirst();
  }

  @Override
  public List<UUID> ids(UUID despuesDe, UUID hasta, int limite) {
    PageRequest pagina = PageRequest.of(0, limite);
    return despuesDe == null
        ? jpa.idsEnEstadoHasta(BORRADOR, hasta, pagina)
        : jpa.idsEnEstadoEntre(BORRADOR, despuesDe, hasta, pagina);
  }
}
