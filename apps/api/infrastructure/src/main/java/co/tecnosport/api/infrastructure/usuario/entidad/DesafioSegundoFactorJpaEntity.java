package co.tecnosport.api.infrastructure.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desafio_segundo_factor")
public class DesafioSegundoFactorJpaEntity {

  /** SHA-256 del desafío en hexadecimal ({@code HuellaDeToken}); el desafío no se guarda. */
  @Id
  @Column(name = "huella", length = 64)
  private String huella;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  @Column(name = "usado_en")
  private Instant usadoEn;

  protected DesafioSegundoFactorJpaEntity() {}

  public DesafioSegundoFactorJpaEntity(
      String huella, UUID usuarioId, Instant creadoEn, Instant expiraEn, Instant usadoEn) {
    this.huella = huella;
    this.usuarioId = usuarioId;
    this.creadoEn = creadoEn;
    this.expiraEn = expiraEn;
    this.usadoEn = usadoEn;
  }

  public String getHuella() {
    return huella;
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
