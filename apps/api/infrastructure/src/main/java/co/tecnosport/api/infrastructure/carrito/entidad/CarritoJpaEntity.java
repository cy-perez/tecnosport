package co.tecnosport.api.infrastructure.carrito.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carrito")
public class CarritoJpaEntity {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected CarritoJpaEntity() {}

  public CarritoJpaEntity(UUID id, UUID usuarioId, Instant creadoEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
