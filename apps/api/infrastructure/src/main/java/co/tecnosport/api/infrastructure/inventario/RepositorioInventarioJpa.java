package co.tecnosport.api.infrastructure.inventario;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import co.tecnosport.api.infrastructure.inventario.entidad.InventarioJpaEntity;
import co.tecnosport.api.infrastructure.inventario.entidad.MovimientoInventarioJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sin {@code @Transactional} propio a propósito: el bloqueo pesimista de {@code
 * buscarPorVarianteId} solo sirve si la carga, la mutación en memoria del agregado y {@code
 * guardar} corren dentro de una única transacción abierta por quien llama — este adaptador no puede
 * abrirla por su cuenta sin romper esa garantía (ver ADR y comentario en {@link
 * co.tecnosport.api.application.inventario.RepositorioInventario}).
 */
@Component
public class RepositorioInventarioJpa implements RepositorioInventario {

  private final InventarioJpaRepository inventarios;
  private final MovimientoInventarioJpaRepository movimientos;

  public RepositorioInventarioJpa(
      InventarioJpaRepository inventarios, MovimientoInventarioJpaRepository movimientos) {
    this.inventarios = Objects.requireNonNull(inventarios);
    this.movimientos = Objects.requireNonNull(movimientos);
  }

  @Override
  public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
    return inventarios
        .findByVarianteId(varianteId)
        .map(entidad -> aInventario(entidad, movimientos.findByInventarioId(entidad.getId())));
  }

  @Override
  public void guardar(Inventario inventario) {
    if (inventarios.findById(inventario.id()).isEmpty()) {
      inventarios.save(new InventarioJpaEntity(inventario.id(), inventario.varianteId()));
    }
    List<MovimientoInventarioJpaEntity> entidades =
        inventario.movimientos().stream().map(m -> aEntidad(inventario.id(), m)).toList();
    movimientos.saveAll(entidades);
  }

  private Inventario aInventario(
      InventarioJpaEntity entidad, List<MovimientoInventarioJpaEntity> movimientosJpa) {
    List<MovimientoInventario> dominio = movimientosJpa.stream().map(this::aMovimiento).toList();
    return new Inventario(entidad.getId(), entidad.getVarianteId(), dominio);
  }

  private MovimientoInventario aMovimiento(MovimientoInventarioJpaEntity m) {
    return new MovimientoInventario(
        m.getId(),
        TipoMovimientoInventario.valueOf(m.getTipo()),
        m.getCantidad(),
        m.getCreadoEn(),
        m.getExpiraEn(),
        m.getReferenciaId(),
        m.getMotivo());
  }

  private MovimientoInventarioJpaEntity aEntidad(UUID inventarioId, MovimientoInventario m) {
    return new MovimientoInventarioJpaEntity(
        m.id(),
        inventarioId,
        m.tipo().name(),
        m.cantidad(),
        m.creadoEn(),
        m.expiraEn(),
        m.referenciaId(),
        m.motivo());
  }
}
