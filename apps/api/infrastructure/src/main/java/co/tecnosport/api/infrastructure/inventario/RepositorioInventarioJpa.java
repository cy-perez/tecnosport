package co.tecnosport.api.infrastructure.inventario;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import co.tecnosport.api.infrastructure.inventario.entidad.InventarioJpaEntity;
import co.tecnosport.api.infrastructure.inventario.entidad.MovimientoInventarioJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

  /**
   * Sin {@code @Lock}, a diferencia de {@link #buscarPorVarianteId}: esto lo llama una pantalla de
   * solo lectura del panel y bloquear el catálogo entero para pintarla sería un despropósito.
   *
   * <p>Dos consultas y un agrupamiento en memoria, no una por inventario: {@code
   * findByInventarioId} dentro de un bucle serían tantas consultas como variantes tenga el
   * catálogo. Lo que sí trae entero es el histórico de movimientos, con el precio que adr/0049 deja
   * escrito.
   */
  @Override
  public List<Inventario> listarTodos() {
    Map<UUID, List<MovimientoInventarioJpaEntity>> porInventario =
        movimientos.findAll().stream()
            .collect(Collectors.groupingBy(MovimientoInventarioJpaEntity::getInventarioId));
    return inventarios.findAll().stream()
        .map(
            entidad -> aInventario(entidad, porInventario.getOrDefault(entidad.getId(), List.of())))
        .toList();
  }

  /**
   * El mismo agrupamiento en memoria que {@link #listarTodos}, acotado a las variantes que se
   * piden: dos consultas, no una por variante. Sin {@code @Lock} por el mismo motivo, con el
   * agravante de que aquí quien llama es la vitrina y no una pantalla del panel.
   *
   * <p>Un conjunto vacío no llega a la base: {@code IN ()} no es SQL válido en Postgres y Spring
   * Data lo traduce a una consulta que nunca trae nada. Se corta antes y se ahorra el viaje.
   */
  @Override
  public List<Inventario> buscarPorVarianteIds(Collection<UUID> varianteIds) {
    if (varianteIds == null || varianteIds.isEmpty()) {
      return List.of();
    }
    List<InventarioJpaEntity> libros = inventarios.findAllByVarianteIdIn(varianteIds);
    if (libros.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<MovimientoInventarioJpaEntity>> porInventario =
        movimientos
            .findByInventarioIdIn(libros.stream().map(InventarioJpaEntity::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(MovimientoInventarioJpaEntity::getInventarioId));
    return libros.stream()
        .map(
            entidad -> aInventario(entidad, porInventario.getOrDefault(entidad.getId(), List.of())))
        .toList();
  }

  /**
   * Inserta si hace falta y vuelve a leer con bloqueo. El {@code findByVarianteId} de la segunda
   * línea es el que toma el {@code select … for update}, y para entonces la fila existe seguro — la
   * haya puesto esta transacción o la que ganó la carrera.
   *
   * <p>El id se genera aquí y puede acabar descartándolo el {@code on conflict}: es el precio de no
   * tener que preguntar antes si existe, y preguntar antes es justo lo que no sirve, porque entre
   * la pregunta y la inserción cabe la otra transacción.
   */
  @Override
  public Inventario abrirLibroConBloqueo(UUID varianteId) {
    inventarios.abrirSiNoExiste(GeneradorIdentificador.nuevo(), varianteId);
    return buscarPorVarianteId(varianteId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "El libro de la variante " + varianteId + " no existe después de abrirlo."));
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
