package co.tecnosport.api.infrastructure.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_verificacion_correo")
public class TokenVerificacionCorreoJpaEntity {

  @Id private UUID id;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  @Column(name = "usado_en")
  private Instant usadoEn;

  protected TokenVerificacionCorreoJpaEntity() {}

  public TokenVerificacionCorreoJpaEntity(
      UUID id, UUID usuarioId, Instant creadoEn, Instant expiraEn, Instant usadoEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.creadoEn = creadoEn;
    this.expiraEn = expiraEn;
    this.usadoEn = usadoEn;
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

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public Instant getUsadoEn() {
    return usadoEn;
  }
}
