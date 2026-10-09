package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "variante_de_proveedor")
public class VarianteDeProveedorJpaEntity {

  @Id
  @Column(name = "variante_id")
  private UUID varianteId;

  @Column(name = "producto_id", nullable = false)
  private UUID productoId;

  @Column(name = "proveedor_id", nullable = false)
  private UUID proveedorId;

  @Column(nullable = false)
  private String configuracion;

  @Column(nullable = false)
  private String color;

  @Column(nullable = false)
  private BigDecimal costo;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected VarianteDeProveedorJpaEntity() {}

  public VarianteDeProveedorJpaEntity(
      UUID varianteId,
      UUID productoId,
      UUID proveedorId,
      String configuracion,
      String color,
      BigDecimal costo,
      Instant actualizadoEn) {
    this.varianteId = varianteId;
    this.productoId = productoId;
    this.proveedorId = proveedorId;
    this.configuracion = configuracion;
    this.color = color;
    this.costo = costo;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public String getConfiguracion() {
    return configuracion;
  }

  public String getColor() {
    return color;
  }

  public BigDecimal getCosto() {
    return costo;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
