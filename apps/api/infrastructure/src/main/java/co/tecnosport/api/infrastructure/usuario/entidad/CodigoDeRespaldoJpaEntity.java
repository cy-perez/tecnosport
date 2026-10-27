package co.tecnosport.api.infrastructure.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "codigo_de_respaldo")
public class CodigoDeRespaldoJpaEntity {

  /** SHA-256 del código en hexadecimal ({@code HuellaDeToken}); el código no se guarda. */
  @Id
  @Column(name = "huella", length = 64)
  private String huella;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "usado_en")
  private Instant usadoEn;

  protected CodigoDeRespaldoJpaEntity() {}

  public CodigoDeRespaldoJpaEntity(
      String huella, UUID usuarioId, Instant creadoEn, Instant usadoEn) {
    this.huella = huella;
    this.usuarioId = usuarioId;
    this.creadoEn = creadoEn;
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

  public Instant getUsadoEn() {
    return usadoEn;
  }
}
