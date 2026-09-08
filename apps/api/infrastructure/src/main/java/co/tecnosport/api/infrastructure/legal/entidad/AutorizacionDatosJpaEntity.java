package co.tecnosport.api.infrastructure.legal.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "autorizacion_datos")
public class AutorizacionDatosJpaEntity {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "correo", nullable = false)
  private String correo;

  @Column(name = "version_politica", nullable = false)
  private String versionPolitica;

  @Column(name = "direccion_ip", nullable = false)
  private String direccionIp;

  @Column(name = "origen", nullable = false)
  private String origen;

  @Column(name = "otorgada_en", nullable = false)
  private Instant otorgadaEn;

  protected AutorizacionDatosJpaEntity() {}

  public AutorizacionDatosJpaEntity(
      UUID id,
      UUID usuarioId,
      String correo,
      String versionPolitica,
      String direccionIp,
      String origen,
      Instant otorgadaEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.correo = correo;
    this.versionPolitica = versionPolitica;
    this.direccionIp = direccionIp;
    this.origen = origen;
    this.otorgadaEn = otorgadaEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getCorreo() {
    return correo;
  }

  public String getVersionPolitica() {
    return versionPolitica;
  }

  public String getDireccionIp() {
    return direccionIp;
  }

  public String getOrigen() {
    return origen;
  }

  public Instant getOtorgadaEn() {
    return otorgadaEn;
  }
}
