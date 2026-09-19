package co.tecnosport.api.infrastructure.correo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "correo_pendiente")
public class CorreoPendienteJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String destinatario;

  @Column(nullable = false)
  private String asunto;

  @Column(name = "cuerpo_html", nullable = false)
  private String cuerpoHtml;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "proximo_intento_en", nullable = false)
  private Instant proximoIntentoEn;

  @Column(nullable = false)
  private int intentos;

  @Column(name = "enviado_en")
  private Instant enviadoEn;

  @Column(name = "ultimo_error")
  private String ultimoError;

  protected CorreoPendienteJpaEntity() {}

  public CorreoPendienteJpaEntity(
      UUID id,
      String destinatario,
      String asunto,
      String cuerpoHtml,
      Instant creadoEn,
      Instant proximoIntentoEn,
      int intentos,
      Instant enviadoEn,
      String ultimoError) {
    this.id = id;
    this.destinatario = destinatario;
    this.asunto = asunto;
    this.cuerpoHtml = cuerpoHtml;
    this.creadoEn = creadoEn;
    this.proximoIntentoEn = proximoIntentoEn;
    this.intentos = intentos;
    this.enviadoEn = enviadoEn;
    this.ultimoError = ultimoError;
  }

  public UUID getId() {
    return id;
  }

  public String getDestinatario() {
    return destinatario;
  }

  public String getAsunto() {
    return asunto;
  }

  public String getCuerpoHtml() {
    return cuerpoHtml;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getProximoIntentoEn() {
    return proximoIntentoEn;
  }

  public int getIntentos() {
    return intentos;
  }

  public Instant getEnviadoEn() {
    return enviadoEn;
  }

  public String getUltimoError() {
    return ultimoError;
  }
}
