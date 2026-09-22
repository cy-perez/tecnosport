package co.tecnosport.api.infrastructure.inventario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Implementa {@link Persistable} para decirle a Spring Data lo que el {@code @Id} asignado le
 * oculta: que una entidad recién construida es nueva. Sin esto, {@code save} no puede saberlo —no
 * hay {@code @Version} ni id generado por la base— y cae en {@code merge}, que antes de insertar
 * hace su propio {@code SELECT}.
 *
 * <p>Se puede afirmar sin más que es nueva porque esta tabla es de solo-agregar: un movimiento no
 * se modifica nunca, así que la única razón para construir una de estas es escribirla por primera
 * vez. Quien las lee recibe entidades gestionadas por Hibernate, que no pasan por aquí.
 */
@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventarioJpaEntity implements Persistable<UUID> {

  @Id private UUID id;

  @Transient private final boolean nueva;

  @Column(name = "inventario_id", nullable = false)
  private UUID inventarioId;

  @Column(nullable = false)
  private String tipo;

  @Column(nullable = false)
  private int cantidad;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "expira_en")
  private Instant expiraEn;

  @Column(name = "referencia_id")
  private UUID referenciaId;

  private String motivo;

  /** El que usa Hibernate al leer: lo que viene de la base no es nuevo. */
  protected MovimientoInventarioJpaEntity() {
    this.nueva = false;
  }

  public MovimientoInventarioJpaEntity(
      UUID id,
      UUID inventarioId,
      String tipo,
      int cantidad,
      Instant creadoEn,
      Instant expiraEn,
      UUID referenciaId,
      String motivo) {
    this.nueva = true;
    this.id = id;
    this.inventarioId = inventarioId;
    this.tipo = tipo;
    this.cantidad = cantidad;
    this.creadoEn = creadoEn;
    this.expiraEn = expiraEn;
    this.referenciaId = referenciaId;
    this.motivo = motivo;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return nueva;
  }

  public UUID getInventarioId() {
    return inventarioId;
  }

  public String getTipo() {
    return tipo;
  }

  public int getCantidad() {
    return cantidad;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public UUID getReferenciaId() {
    return referenciaId;
  }

  public String getMotivo() {
    return motivo;
  }
}
