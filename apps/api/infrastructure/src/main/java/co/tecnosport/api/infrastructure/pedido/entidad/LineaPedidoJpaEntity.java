package co.tecnosport.api.infrastructure.pedido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "linea_pedido")
public class LineaPedidoJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(name = "variante_id", nullable = false)
  private UUID varianteId;

  @Column(nullable = false)
  private String sku;

  @Column(nullable = false)
  private String nombre;

  @Column(nullable = false)
  private int cantidad;

  @Column(name = "precio_unitario", nullable = false)
  private BigDecimal precioUnitario;

  @Column(name = "tasa_iva", nullable = false)
  private BigDecimal tasaIva;

  @Column(name = "imagen_url")
  private String imagenUrl;

  @Column(name = "id_reserva", nullable = false)
  private UUID idReserva;

  protected LineaPedidoJpaEntity() {}

  public LineaPedidoJpaEntity(
      UUID id,
      UUID pedidoId,
      UUID varianteId,
      String sku,
      String nombre,
      int cantidad,
      BigDecimal precioUnitario,
      BigDecimal tasaIva,
      String imagenUrl,
      UUID idReserva) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.varianteId = varianteId;
    this.sku = sku;
    this.nombre = nombre;
    this.cantidad = cantidad;
    this.precioUnitario = precioUnitario;
    this.tasaIva = tasaIva;
    this.imagenUrl = imagenUrl;
    this.idReserva = idReserva;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public String getSku() {
    return sku;
  }

  public String getNombre() {
    return nombre;
  }

  public int getCantidad() {
    return cantidad;
  }

  public BigDecimal getPrecioUnitario() {
    return precioUnitario;
  }

  public BigDecimal getTasaIva() {
    return tasaIva;
  }

  public String getImagenUrl() {
    return imagenUrl;
  }

  public UUID getIdReserva() {
    return idReserva;
  }
}
