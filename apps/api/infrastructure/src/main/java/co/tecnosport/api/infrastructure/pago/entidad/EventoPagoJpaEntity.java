package co.tecnosport.api.infrastructure.pago.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code id} es un identificador de fila propio de esta tabla, sin equivalente en el dominio:
 * {@code EventoPago} no lleva uno (a diferencia de {@code HistorialPedido}) porque nada necesita
 * referenciar una fila de este tipo entre guardados — cada {@code guardar} de {@code Pago} borra y
 * reinserta todos sus eventos, igual que {@code linea_pedido}/{@code historial_pedido}.
 */
@Entity
@Table(name = "evento_pago")
public class EventoPagoJpaEntity {

  @Id private UUID id;

  @Column(name = "pago_id", nullable = false)
  private UUID pagoId;

  @Column(name = "id_evento", nullable = false)
  private String idEvento;

  @Column(nullable = false)
  private String estado;

  @Column(name = "recibido_en", nullable = false)
  private Instant recibidoEn;

  protected EventoPagoJpaEntity() {}

  public EventoPagoJpaEntity(
      UUID id, UUID pagoId, String idEvento, String estado, Instant recibidoEn) {
    this.id = id;
    this.pagoId = pagoId;
    this.idEvento = idEvento;
    this.estado = estado;
    this.recibidoEn = recibidoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPagoId() {
    return pagoId;
  }

  public String getIdEvento() {
    return idEvento;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getRecibidoEn() {
    return recibidoEn;
  }
}
