package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Las medidas de la bolsa de referencia. Una sola fila, con {@code id = 1} impuesto por la base
 * ({@code V89}): son transversales y no hay una segunda bolsa que elegir.
 */
@Entity
@Table(name = "envio_medidas_referencia")
public class MedidasDeReferenciaJpaEntity {

  /** La única fila que la restricción de la base deja existir. */
  public static final short UNICA = 1;

  @Id private Short id;

  @Column(name = "largo_cm", nullable = false)
  private int largoCm;

  @Column(name = "ancho_cm", nullable = false)
  private int anchoCm;

  @Column(name = "alto_cm", nullable = false)
  private int altoCm;

  protected MedidasDeReferenciaJpaEntity() {}

  public MedidasDeReferenciaJpaEntity(int largoCm, int anchoCm, int altoCm) {
    this.id = UNICA;
    this.largoCm = largoCm;
    this.anchoCm = anchoCm;
    this.altoCm = altoCm;
  }

  public Short getId() {
    return id;
  }

  public int getLargoCm() {
    return largoCm;
  }

  public int getAnchoCm() {
    return anchoCm;
  }

  public int getAltoCm() {
    return altoCm;
  }
}
