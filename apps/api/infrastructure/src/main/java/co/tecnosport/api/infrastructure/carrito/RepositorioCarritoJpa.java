package co.tecnosport.api.infrastructure.carrito;

import co.tecnosport.api.application.carrito.RepositorioCarrito;
import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarrito;
import co.tecnosport.api.infrastructure.carrito.entidad.CarritoJpaEntity;
import co.tecnosport.api.infrastructure.carrito.entidad.LineaCarritoJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A diferencia de inventario, las líneas de carrito sí se editan y se borran: no son un histórico
 * de solo-agregar. {@code guardar} reemplaza todas las líneas de una — correcto y simple a esta
 * escala (un carrito nunca tiene más que unas pocas). Sin bloqueo de otro agregado que respetar
 * aquí, así que este adaptador sí puede abrir su propia transacción.
 */
@Component
public class RepositorioCarritoJpa implements RepositorioCarrito {

  private final CarritoJpaRepository carritos;
  private final LineaCarritoJpaRepository lineas;

  public RepositorioCarritoJpa(CarritoJpaRepository carritos, LineaCarritoJpaRepository lineas) {
    this.carritos = Objects.requireNonNull(carritos);
    this.lineas = Objects.requireNonNull(lineas);
  }

  @Override
  public Optional<Carrito> buscarPorId(UUID carritoId) {
    return carritos
        .findById(carritoId)
        .map(entidad -> aCarrito(entidad, lineas.findByCarritoId(entidad.getId())));
  }

  @Override
  @Transactional
  public void guardar(Carrito carrito) {
    if (carritos.findById(carrito.id()).isEmpty()) {
      carritos.save(
          new CarritoJpaEntity(
              carrito.id(),
              carrito.usuarioId().orElse(null),
              carrito.creadoEn(),
              carrito.actualizadoEn()));
    }
    lineas.deleteByCarritoId(carrito.id());
    List<LineaCarritoJpaEntity> entidades =
        carrito.lineas().stream().map(linea -> aEntidad(carrito.id(), linea)).toList();
    lineas.saveAll(entidades);
  }

  private Carrito aCarrito(CarritoJpaEntity entidad, List<LineaCarritoJpaEntity> lineasJpa) {
    List<LineaCarrito> dominio = lineasJpa.stream().map(this::aLinea).toList();
    return new Carrito(
        entidad.getId(),
        entidad.getUsuarioId(),
        dominio,
        entidad.getCreadoEn(),
        entidad.getActualizadoEn());
  }

  private LineaCarrito aLinea(LineaCarritoJpaEntity l) {
    return new LineaCarrito(l.getId(), l.getVarianteId(), l.getCantidad());
  }

  private LineaCarritoJpaEntity aEntidad(UUID carritoId, LineaCarrito linea) {
    return new LineaCarritoJpaEntity(linea.id(), carritoId, linea.varianteId(), linea.cantidad());
  }

  @Override
  @Transactional
  public int eliminarInactivosDesde(Instant limite) {
    return carritos.eliminarInactivosDesde(limite);
  }
}
