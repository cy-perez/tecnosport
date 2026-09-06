package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "set_rotacion")
public class SetRotacionJpaEntity {

  @Id private UUID id;

  @Column(name = "producto_id", nullable = false)
  private UUID productoId;

  @Column(name = "variante_id")
  private UUID varianteId;

  /** Cuántos fotogramas se prometieron al abrir el set, no cuántos hay guardados. */
  @Column(nullable = false)
  private int fotogramas;

  @Column(nullable = false)
  private String estado;

  @Column(name = "capturado_por")
  private String capturadoPor;

  @Column(name = "capturado_en")
  private Instant capturadoEn;

  @Column private String dispositivo;

  @Column(name = "version_asistente")
  private String versionAsistente;

  protected SetRotacionJpaEntity() {}

  public SetRotacionJpaEntity(
      UUID id,
      UUID productoId,
      UUID varianteId,
      int fotogramas,
      String estado,
      String capturadoPor,
      Instant capturadoEn,
      String dispositivo,
      String versionAsistente) {
    this.id = id;
    this.productoId = productoId;
    this.varianteId = varianteId;
    this.fotogramas = fotogramas;
    this.estado = estado;
    this.capturadoPor = capturadoPor;
    this.capturadoEn = capturadoEn;
    this.dispositivo = dispositivo;
    this.versionAsistente = versionAsistente;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public int getFotogramas() {
    return fotogramas;
  }

  public String getEstado() {
    return estado;
  }

  public String getCapturadoPor() {
    return capturadoPor;
  }

  public Instant getCapturadoEn() {
    return capturadoEn;
  }

  public String getDispositivo() {
    return dispositivo;
  }

  public String getVersionAsistente() {
    return versionAsistente;
  }
}
