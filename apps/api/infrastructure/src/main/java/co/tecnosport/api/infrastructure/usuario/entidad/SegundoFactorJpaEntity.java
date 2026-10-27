package co.tecnosport.api.infrastructure.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "segundo_factor")
public class SegundoFactorJpaEntity {

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  /** {@code CifradorAesGcm}: nunca la clave en claro. */
  @Column(name = "secreto_cifrado", nullable = false)
  private String claveCifrada;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "activado_en")
  private Instant activadoEn;

  @Column(name = "ultimo_paso_usado")
  private Long ultimoPasoUsado;

  protected SegundoFactorJpaEntity() {}

  public SegundoFactorJpaEntity(
      UUID usuarioId,
      String claveCifrada,
      Instant creadoEn,
      Instant activadoEn,
      Long ultimoPasoUsado) {
    this.usuarioId = usuarioId;
    this.claveCifrada = claveCifrada;
    this.creadoEn = creadoEn;
    this.activadoEn = activadoEn;
    this.ultimoPasoUsado = ultimoPasoUsado;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getClaveCifrada() {
    return claveCifrada;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActivadoEn() {
    return activadoEn;
  }

  public Long getUltimoPasoUsado() {
    return ultimoPasoUsado;
  }
}
