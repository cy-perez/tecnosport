package co.tecnosport.api.infrastructure.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sesion_refresco")
public class SesionRefrescoJpaEntity {

  @Id private UUID id;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(name = "familia_id", nullable = false)
  private UUID familiaId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  @Column(name = "usado_en")
  private Instant usadoEn;

  @Column(name = "revocado_en")
  private Instant revocadoEn;

  protected SesionRefrescoJpaEntity() {}

  public SesionRefrescoJpaEntity(
      UUID id,
      UUID usuarioId,
      UUID familiaId,
      Instant creadoEn,
      Instant expiraEn,
      Instant usadoEn,
      Instant revocadoEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.familiaId = familiaId;
    this.creadoEn = creadoEn;
    this.expiraEn = expiraEn;
    this.usadoEn = usadoEn;
    this.revocadoEn = revocadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getFamiliaId() {
    return familiaId;
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

  public Instant getRevocadoEn() {
    return revocadoEn;
  }
}
