package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** El peso promedio de una categoría ({@code V89}). La llave es la categoría: uno por cada una. */
@Entity
@Table(name = "envio_peso_referencia")
public class PesoDeReferenciaJpaEntity {

  @Id
  @Column(name = "categoria_id")
  private UUID categoriaId;

  @Column(name = "peso_gramos", nullable = false)
  private int pesoGramos;

  protected PesoDeReferenciaJpaEntity() {}

  public PesoDeReferenciaJpaEntity(UUID categoriaId, int pesoGramos) {
    this.categoriaId = categoriaId;
    this.pesoGramos = pesoGramos;
  }

  public UUID getCategoriaId() {
    return categoriaId;
  }

  public int getPesoGramos() {
    return pesoGramos;
  }
}
