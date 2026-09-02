package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "variante")
public class VarianteJpaEntity {

  @Id private UUID id;

  @Column(name = "producto_id", nullable = false)
  private UUID productoId;

  @Column(nullable = false)
  private String sku;

  @Column(nullable = false)
  private BigDecimal precio;

  @Column(name = "tasa_iva", nullable = false)
  private BigDecimal tasaIva;

  @Column(nullable = false)
  private int existencia;

  @Column(name = "codigo_barras")
  private String codigoBarras;

  @Column(nullable = false)
  private String estado;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected VarianteJpaEntity() {}

  public VarianteJpaEntity(
      UUID id,
      UUID productoId,
      String sku,
      BigDecimal precio,
      BigDecimal tasaIva,
      int existencia,
      String codigoBarras,
      String estado,
      Instant creadoEn) {
    this.id = id;
    this.productoId = productoId;
    this.sku = sku;
    this.precio = precio;
    this.tasaIva = tasaIva;
    this.existencia = existencia;
    this.codigoBarras = codigoBarras;
    this.estado = estado;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public String getSku() {
    return sku;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public BigDecimal getTasaIva() {
    return tasaIva;
  }

  public int getExistencia() {
    return existencia;
  }

  public String getCodigoBarras() {
    return codigoBarras;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
