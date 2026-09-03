package co.tecnosport.api.infrastructure.pedido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historial_pedido")
public class HistorialPedidoJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(nullable = false)
  private String estado;

  @Column(nullable = false)
  private Instant fecha;

  @Column(nullable = false)
  private String actor;

  private String motivo;

  protected HistorialPedidoJpaEntity() {}

  public HistorialPedidoJpaEntity(
      UUID id, UUID pedidoId, String estado, Instant fecha, String actor, String motivo) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.estado = estado;
    this.fecha = fecha;
    this.actor = actor;
    this.motivo = motivo;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getFecha() {
    return fecha;
  }

  public String getActor() {
    return actor;
  }

  public String getMotivo() {
    return motivo;
  }
}
