package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.UUID;

/** Una fila de {@code borrador_tecnologia_foto}. Los anchos, uno por línea. */
@Embeddable
public class FotoTecnologiaJpaEmbeddable {

  @Column(name = "foto_id", nullable = false)
  private UUID fotoId;

  @Column(nullable = false)
  private String variantes;

  @Column(name = "referencia_vista_previa")
  private String referenciaVistaPrevia;

  @Column(nullable = false)
  private int alto;

  @Column(nullable = false)
  private String hash;

  @Column(name = "alt_es", nullable = false)
  private String altEs;

  @Column(name = "alt_en", nullable = false)
  private String altEn;

  @Column(name = "subida_en", nullable = false)
  private Instant subidaEn;

  protected FotoTecnologiaJpaEmbeddable() {}

  public FotoTecnologiaJpaEmbeddable(
      UUID fotoId,
      String variantes,
      String referenciaVistaPrevia,
      int alto,
      String hash,
      String altEs,
      String altEn,
      Instant subidaEn) {
    this.fotoId = fotoId;
    this.variantes = variantes;
    this.referenciaVistaPrevia = referenciaVistaPrevia;
    this.alto = alto;
    this.hash = hash;
    this.altEs = altEs;
    this.altEn = altEn;
    this.subidaEn = subidaEn;
  }

  public UUID getFotoId() {
    return fotoId;
  }

  public String getVariantes() {
    return variantes;
  }

  public String getReferenciaVistaPrevia() {
    return referenciaVistaPrevia;
  }

  public int getAlto() {
    return alto;
  }

  public String getHash() {
    return hash;
  }

  public String getAltEs() {
    return altEs;
  }

  public String getAltEn() {
    return altEn;
  }

  public Instant getSubidaEn() {
    return subidaEn;
  }
}
