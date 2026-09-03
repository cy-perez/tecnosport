package co.tecnosport.api.infrastructure.compartido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotencia")
public class IdempotenciaJpaEntity {

  @Id private String llave;

  @Column(nullable = false)
  private String metodo;

  @Column(nullable = false)
  private String ruta;

  @Column(nullable = false)
  private String estado;

  @Column(name = "estado_http")
  private Integer estadoHttp;

  @Column(name = "tipo_contenido")
  private String tipoContenido;

  private String cuerpo;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "completado_en")
  private Instant completadoEn;

  protected IdempotenciaJpaEntity() {}

  public IdempotenciaJpaEntity(
      String llave,
      String metodo,
      String ruta,
      String estado,
      Integer estadoHttp,
      String tipoContenido,
      String cuerpo,
      Instant creadoEn,
      Instant completadoEn) {
    this.llave = llave;
    this.metodo = metodo;
    this.ruta = ruta;
    this.estado = estado;
    this.estadoHttp = estadoHttp;
    this.tipoContenido = tipoContenido;
    this.cuerpo = cuerpo;
    this.creadoEn = creadoEn;
    this.completadoEn = completadoEn;
  }

  public String getLlave() {
    return llave;
  }

  public String getMetodo() {
    return metodo;
  }

  public String getRuta() {
    return ruta;
  }

  public String getEstado() {
    return estado;
  }

  public Integer getEstadoHttp() {
    return estadoHttp;
  }

  public String getTipoContenido() {
    return tipoContenido;
  }

  public String getCuerpo() {
    return cuerpo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getCompletadoEn() {
    return completadoEn;
  }
}
