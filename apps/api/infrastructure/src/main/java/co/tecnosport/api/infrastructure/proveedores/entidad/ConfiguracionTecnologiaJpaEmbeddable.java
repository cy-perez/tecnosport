package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Una fila de {@code borrador_tecnologia_configuracion}. Los colores, uno por línea. */
@Embeddable
public class ConfiguracionTecnologiaJpaEmbeddable {

  @Column(nullable = false)
  private String sku;

  @Column(nullable = false)
  private String titulo;

  @Column private String ram;

  @Column private String almacenamiento;

  @Column private String sim;

  @Column(name = "costo_proveedor", nullable = false)
  private BigDecimal costoProveedor;

  @Column(name = "precio_mercado")
  private BigDecimal precioMercado;

  @Column(name = "colores_sugeridos")
  private String coloresSugeridos;

  @Column(name = "colores_elegidos")
  private String coloresElegidos;

  @Column(name = "precio_venta")
  private BigDecimal precioVenta;

  protected ConfiguracionTecnologiaJpaEmbeddable() {}

  public ConfiguracionTecnologiaJpaEmbeddable(
      String sku,
      String titulo,
      String ram,
      String almacenamiento,
      String sim,
      BigDecimal costoProveedor,
      BigDecimal precioMercado,
      String coloresSugeridos,
      String coloresElegidos,
      BigDecimal precioVenta) {
    this.sku = sku;
    this.titulo = titulo;
    this.ram = ram;
    this.almacenamiento = almacenamiento;
    this.sim = sim;
    this.costoProveedor = costoProveedor;
    this.precioMercado = precioMercado;
    this.coloresSugeridos = coloresSugeridos;
    this.coloresElegidos = coloresElegidos;
    this.precioVenta = precioVenta;
  }

  public String getSku() {
    return sku;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getRam() {
    return ram;
  }

  public String getAlmacenamiento() {
    return almacenamiento;
  }

  public String getSim() {
    return sim;
  }

  public BigDecimal getCostoProveedor() {
    return costoProveedor;
  }

  public BigDecimal getPrecioMercado() {
    return precioMercado;
  }

  public String getColoresSugeridos() {
    return coloresSugeridos;
  }

  public String getColoresElegidos() {
    return coloresElegidos;
  }

  public BigDecimal getPrecioVenta() {
    return precioVenta;
  }
}
