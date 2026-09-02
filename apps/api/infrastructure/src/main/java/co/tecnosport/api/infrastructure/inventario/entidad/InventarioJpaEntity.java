package co.tecnosport.api.infrastructure.inventario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "inventario")
public class InventarioJpaEntity {

  @Id private UUID id;

  @Column(name = "variante_id", nullable = false)
  private UUID varianteId;

  protected InventarioJpaEntity() {}

  public InventarioJpaEntity(UUID id, UUID varianteId) {
    this.id = id;
    this.varianteId = varianteId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getVarianteId() {
    return varianteId;
  }
}
