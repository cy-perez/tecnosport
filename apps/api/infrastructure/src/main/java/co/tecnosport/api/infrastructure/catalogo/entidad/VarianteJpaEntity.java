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

  /** El publicado: el que se anuncia, con la comisión de la pasarela dentro (ADR-0095). */
  @Column(nullable = false)
  private BigDecimal precio;

  /** El que el negocio recibe neto: el que paga quien compra sin pasarela (V123, ADR-0095). */
  @Column(name = "precio_contado", nullable = false)
  private BigDecimal precioContado;

  @Column(name = "tasa_iva", nullable = false)
  private BigDecimal tasaIva;

  @Column(name = "codigo_barras")
  private String codigoBarras;

  @Column(name = "peso_gramos")
  // Integer y no int desde la V55: las cuatro columnas son nulables porque una variante puede
  // venderse sin medir, solo con recogida en el punto. Un primitivo las leeria como cero, que es
  // justo la confusion que hay que evitar — "no lo se" y "mide cero" no son lo mismo.
  private Integer pesoGramos;

  @Column(name = "largo_cm")
  private Integer largoCm;

  @Column(name = "ancho_cm")
  private Integer anchoCm;

  @Column(name = "alto_cm")
  private Integer altoCm;

  @Column(nullable = false)
  private String estado;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  /**
   * El SKU con que el proveedor vende la prenda de esta variante: el del pie de su foto en un álbum
   * de La Riverah (V114, 13 de octubre de 2026). Nulo en las demás. Fuera del constructor a
   * propósito: quien reescribe una variante con {@code save(new ...)} tiene que copiarlo con {@link
   * #conReferenciaProveedor}, o lo borra.
   */
  @Column(name = "referencia_proveedor")
  private String referenciaProveedor;

  /**
   * El proveedor anunció agotado el SKU de esta prenda (V114): sigue activa, pero no se vende.
   * Fuera del constructor, como {@link #referenciaProveedor}, y por lo mismo.
   */
  @Column(name = "agotada_por_proveedor", nullable = false)
  private boolean agotadaPorProveedor;

  /**
   * Su color de tecnología no está confirmado con el proveedor (V117): no se compra sin enlace de
   * compra. Fuera del constructor, como {@link #agotadaPorProveedor}, y por lo mismo.
   */
  @Column(name = "disponibilidad_por_confirmar", nullable = false)
  private boolean disponibilidadPorConfirmar;

  protected VarianteJpaEntity() {}

  public VarianteJpaEntity(
      UUID id,
      UUID productoId,
      String sku,
      BigDecimal precio,
      BigDecimal precioContado,
      BigDecimal tasaIva,
      String codigoBarras,
      Integer pesoGramos,
      Integer largoCm,
      Integer anchoCm,
      Integer altoCm,
      String estado,
      Instant creadoEn) {
    this.id = id;
    this.productoId = productoId;
    this.sku = sku;
    this.precio = precio;
    this.precioContado = precioContado;
    this.tasaIva = tasaIva;
    this.codigoBarras = codigoBarras;
    this.pesoGramos = pesoGramos;
    this.largoCm = largoCm;
    this.anchoCm = anchoCm;
    this.altoCm = altoCm;
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

  public BigDecimal getPrecioContado() {
    return precioContado;
  }

  public BigDecimal getTasaIva() {
    return tasaIva;
  }

  public String getCodigoBarras() {
    return codigoBarras;
  }

  public Integer getPesoGramos() {
    return pesoGramos;
  }

  public Integer getLargoCm() {
    return largoCm;
  }

  public Integer getAnchoCm() {
    return anchoCm;
  }

  public Integer getAltoCm() {
    return altoCm;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public String getReferenciaProveedor() {
    return referenciaProveedor;
  }

  /** Esta misma entidad con el SKU del proveedor; nulo lo quita. */
  public VarianteJpaEntity conReferenciaProveedor(String referencia) {
    this.referenciaProveedor = referencia;
    return this;
  }

  public boolean isAgotadaPorProveedor() {
    return agotadaPorProveedor;
  }

  /** Esta misma entidad, agotada por el proveedor o de nuevo a la venta. */
  public VarianteJpaEntity conAgotadaPorProveedor(boolean agotada) {
    this.agotadaPorProveedor = agotada;
    return this;
  }

  public boolean isDisponibilidadPorConfirmar() {
    return disponibilidadPorConfirmar;
  }

  /** Esta misma entidad, con su color por confirmar o ya sabido. */
  public VarianteJpaEntity conDisponibilidadPorConfirmar(boolean porConfirmar) {
    this.disponibilidadPorConfirmar = porConfirmar;
    return this;
  }
}
