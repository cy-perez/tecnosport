package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * El acuse de revisión. Dos columnas de referencia y no una: la de la guía y la de la emisión,
 * excluyentes por restricción de la base, para que la llave foránea siga existiendo.
 */
@Entity
@Table(name = "acuse_revision")
public class AcuseDeRevisionJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String tipo;

  @Column(name = "guia_id")
  private UUID guiaId;

  @Column(name = "emision_id")
  private UUID emisionId;

  @Column(name = "revisado_en", nullable = false)
  private Instant revisadoEn;

  @Column(nullable = false)
  private String actor;

  @Column private String nota;

  protected AcuseDeRevisionJpaEntity() {}

  public AcuseDeRevisionJpaEntity(
      UUID id,
      String tipo,
      UUID guiaId,
      UUID emisionId,
      Instant revisadoEn,
      String actor,
      String nota) {
    this.id = id;
    this.tipo = tipo;
    this.guiaId = guiaId;
    this.emisionId = emisionId;
    this.revisadoEn = revisadoEn;
    this.actor = actor;
    this.nota = nota;
  }

  public UUID getId() {
    return id;
  }

  public String getTipo() {
    return tipo;
  }

  public UUID getGuiaId() {
    return guiaId;
  }

  public UUID getEmisionId() {
    return emisionId;
  }

  public Instant getRevisadoEn() {
    return revisadoEn;
  }

  public String getActor() {
    return actor;
  }

  public String getNota() {
    return nota;
  }
}
