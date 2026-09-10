package co.tecnosport.api.infrastructure.atencion.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La prórroga y la respuesta son columnas de esta fila: son valores del agregado, no entidades. */
@Entity
@Table(name = "solicitud_atencion")
public class SolicitudAtencionJpaEntity {

  @Id private UUID id;

  @Column(name = "numero_radicado", nullable = false)
  private String numeroRadicado;

  @Column(nullable = false)
  private String tipo;

  @Column(nullable = false)
  private String correo;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "recibida_en", nullable = false)
  private Instant recibidaEn;

  @Column(name = "radicada_en", nullable = false)
  private Instant radicadaEn;

  @Column(name = "radicada_por", nullable = false)
  private String radicadaPor;

  @Column(nullable = false)
  private String asunto;

  @Column(nullable = false)
  private String estado;

  @Column(name = "prorroga_otorgada_en")
  private Instant prorrogaOtorgadaEn;

  @Column(name = "prorroga_otorgada_por")
  private String prorrogaOtorgadaPor;

  @Column(name = "prorroga_motivo")
  private String prorrogaMotivo;

  @Column(name = "prorroga_avisada_en")
  private Instant prorrogaAvisadaEn;

  @Column(name = "respuesta_en")
  private Instant respuestaEn;

  @Column(name = "respuesta_por")
  private String respuestaPor;

  @Column(name = "respuesta_resumen")
  private String respuestaResumen;

  protected SolicitudAtencionJpaEntity() {}

  public SolicitudAtencionJpaEntity(
      UUID id,
      String numeroRadicado,
      String tipo,
      String correo,
      UUID pedidoId,
      Instant recibidaEn,
      Instant radicadaEn,
      String radicadaPor,
      String asunto,
      String estado,
      Instant prorrogaOtorgadaEn,
      String prorrogaOtorgadaPor,
      String prorrogaMotivo,
      Instant prorrogaAvisadaEn,
      Instant respuestaEn,
      String respuestaPor,
      String respuestaResumen) {
    this.id = id;
    this.numeroRadicado = numeroRadicado;
    this.tipo = tipo;
    this.correo = correo;
    this.pedidoId = pedidoId;
    this.recibidaEn = recibidaEn;
    this.radicadaEn = radicadaEn;
    this.radicadaPor = radicadaPor;
    this.asunto = asunto;
    this.estado = estado;
    this.prorrogaOtorgadaEn = prorrogaOtorgadaEn;
    this.prorrogaOtorgadaPor = prorrogaOtorgadaPor;
    this.prorrogaMotivo = prorrogaMotivo;
    this.prorrogaAvisadaEn = prorrogaAvisadaEn;
    this.respuestaEn = respuestaEn;
    this.respuestaPor = respuestaPor;
    this.respuestaResumen = respuestaResumen;
  }

  public UUID getId() {
    return id;
  }

  public String getNumeroRadicado() {
    return numeroRadicado;
  }

  public String getTipo() {
    return tipo;
  }

  public String getCorreo() {
    return correo;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public Instant getRecibidaEn() {
    return recibidaEn;
  }

  public Instant getRadicadaEn() {
    return radicadaEn;
  }

  public String getRadicadaPor() {
    return radicadaPor;
  }

  public String getAsunto() {
    return asunto;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getProrrogaOtorgadaEn() {
    return prorrogaOtorgadaEn;
  }

  public String getProrrogaOtorgadaPor() {
    return prorrogaOtorgadaPor;
  }

  public String getProrrogaMotivo() {
    return prorrogaMotivo;
  }

  public Instant getProrrogaAvisadaEn() {
    return prorrogaAvisadaEn;
  }

  public Instant getRespuestaEn() {
    return respuestaEn;
  }

  public String getRespuestaPor() {
    return respuestaPor;
  }

  public String getRespuestaResumen() {
    return respuestaResumen;
  }
}
