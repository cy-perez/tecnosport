package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.OrigenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.infrastructure.catalogo.MapeadorCatalogo;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Las dos consultas sobre {@code producto} que solo el contexto de proveedores hace. */
@Repository
public class RepositorioProductosDeProveedorJpa implements RepositorioProductosDeProveedor {

  private final ProductoJpaRepository productos;
  private final MapeadorCatalogo mapeador;

  public RepositorioProductosDeProveedorJpa(
      ProductoJpaRepository productos, MapeadorCatalogo mapeador) {
    this.productos = Objects.requireNonNull(productos);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @Override
  public Optional<Producto> buscarPorHuella(UUID proveedorId, HuellaProveedor huella) {
    return productos
        .findByProveedorIdAndHuellaProveedor(proveedorId, huella.valor())
        .flatMap(p -> mapeador.hidratar(List.of(p.getId())).stream().findFirst());
  }

  @Override
  public List<Producto> disponiblesVistosAntesDe(Instant limite) {
    List<UUID> ids =
        productos
            .findByOrigenAndEstadoDisponibilidadAndVistoPorUltimaVezBefore(
                OrigenProducto.PROVEEDOR.name(), EstadoDisponibilidad.DISPONIBLE.name(), limite)
            .stream()
            .map(ProductoJpaEntity::getId)
            .toList();
    return ids.isEmpty() ? List.of() : mapeador.hidratar(ids);
  }
}
