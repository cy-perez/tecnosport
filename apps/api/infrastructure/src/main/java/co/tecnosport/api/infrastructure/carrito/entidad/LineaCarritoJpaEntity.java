package co.tecnosport.api.infrastructure.carrito.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "linea_carrito")
public class LineaCarritoJpaEntity {

  @Id private UUID id;

  @Column(name = "carrito_id", nullable = false)
  private UUID carritoId;

  @Column(name = "variante_id", nullable = false)
  private UUID varianteId;

  @Column(nullable = false)
  private int cantidad;

  protected LineaCarritoJpaEntity() {}

  public LineaCarritoJpaEntity(UUID id, UUID carritoId, UUID varianteId, int cantidad) {
    this.id = id;
    this.carritoId = carritoId;
    this.varianteId = varianteId;
    this.cantidad = cantidad;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCarritoId() {
    return carritoId;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public int getCantidad() {
    return cantidad;
  }
}
