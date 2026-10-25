package co.tecnosport.api.infrastructure.pedido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un enlace de compra de un color confirmado con el proveedor (V117, ADR-0086). */
@Entity
@Table(name = "enlace_de_compra")
public class EnlaceDeCompraJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String token;

  @Column(name = "variante_id", nullable = false)
  private UUID varianteId;

  @Column(name = "reserva_id", nullable = false)
  private UUID reservaId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  @Column(name = "entrada_propia", nullable = false)
  private boolean entradaPropia;

  @Column(name = "usado_en")
  private Instant usadoEn;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "anulado_en")
  private Instant anuladoEn;

  protected EnlaceDeCompraJpaEntity() {}

  public EnlaceDeCompraJpaEntity(
      UUID id,
      String token,
      UUID varianteId,
      UUID reservaId,
      Instant creadoEn,
      Instant expiraEn,
      boolean entradaPropia,
      Instant usadoEn,
      UUID pedidoId,
      Instant anuladoEn) {
    this.id = id;
    this.token = token;
    this.varianteId = varianteId;
    this.reservaId = reservaId;
    this.creadoEn = creadoEn;
    this.expiraEn = expiraEn;
    this.entradaPropia = entradaPropia;
    this.usadoEn = usadoEn;
    this.pedidoId = pedidoId;
    this.anuladoEn = anuladoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getToken() {
    return token;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public UUID getReservaId() {
    return reservaId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public boolean isEntradaPropia() {
    return entradaPropia;
  }

  public Instant getUsadoEn() {
    return usadoEn;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public Instant getAnuladoEn() {
    return anuladoEn;
  }
}
