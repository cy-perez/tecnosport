package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.UUID;

/** Una fila de {@code borrador_foto_subida}: la foto que quien revisa subió desde el panel. */
@Embeddable
public class FotoSubidaJpaEmbeddable {

  @Column(nullable = false)
  private UUID id;

  @Column(name = "referencia_archivo", nullable = false)
  private String referenciaArchivo;

  @Column(name = "subida_en", nullable = false)
  private Instant subidaEn;

  protected FotoSubidaJpaEmbeddable() {}

  public FotoSubidaJpaEmbeddable(UUID id, String referenciaArchivo, Instant subidaEn) {
    this.id = id;
    this.referenciaArchivo = referenciaArchivo;
    this.subidaEn = subidaEn;
  }

  public UUID getId() {
    return id;
  }

  public String getReferenciaArchivo() {
    return referenciaArchivo;
  }

  public Instant getSubidaEn() {
    return subidaEn;
  }
}
