package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_seguimiento")
public class EventoSeguimientoJpaEntity {

  @Id private UUID id;

  @Column(name = "envio_id", nullable = false)
  private UUID envioId;

  @Column(nullable = false)
  private String estado;

  private String descripcion;

  @Column(name = "ocurrio_en", nullable = false)
  private Instant ocurrioEn;

  @Column(name = "recibido_en", nullable = false)
  private Instant recibidoEn;

  @Column(name = "id_externo", nullable = false)
  private String idExterno;

  protected EventoSeguimientoJpaEntity() {}

  public EventoSeguimientoJpaEntity(
      UUID id,
      UUID envioId,
      String estado,
      String descripcion,
      Instant ocurrioEn,
      Instant recibidoEn,
      String idExterno) {
    this.id = id;
    this.envioId = envioId;
    this.estado = estado;
    this.descripcion = descripcion;
    this.ocurrioEn = ocurrioEn;
    this.recibidoEn = recibidoEn;
    this.idExterno = idExterno;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEnvioId() {
    return envioId;
  }

  public String getEstado() {
    return estado;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public Instant getOcurrioEn() {
    return ocurrioEn;
  }

  public Instant getRecibidoEn() {
    return recibidoEn;
  }

  public String getIdExterno() {
    return idExterno;
  }
}
