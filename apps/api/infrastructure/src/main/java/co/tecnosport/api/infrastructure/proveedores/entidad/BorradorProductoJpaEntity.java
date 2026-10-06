package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "borrador_producto")
public class BorradorProductoJpaEntity {

  @Id private UUID id;

  @Column(name = "publicacion_id", nullable = false)
  private UUID publicacionId;

  @Column(name = "proveedor_id", nullable = false)
  private UUID proveedorId;

  @Column(name = "extraccion_cruda", nullable = false)
  private String extraccionCruda;

  @Column private String titulo;

  @Column private String linea;

  @Column(nullable = false)
  private String tipo;

  @Column(name = "precio_proveedor")
  private BigDecimal precioProveedor;

  @Column(name = "precio_venta_sugerido")
  private BigDecimal precioVentaSugerido;

  @Column(name = "tallas_tipo", nullable = false)
  private String tallasTipo;

  @Column(name = "tallas_sirve_hasta")
  private String tallasSirveHasta;

  @Column(name = "tallas_valores")
  private String tallasValores;

  @Column(name = "cantidad_tonos")
  private Integer cantidadTonos;

  @Column(name = "tonos_nombrados")
  private String tonosNombrados;

  @Column private String material;

  @Column private String descripcion;

  @Column(name = "alt_en")
  private String altEn;

  @Column private String huella;

  @Column private String phash;

  @Column(nullable = false)
  private String alertas;

  @Column(name = "fotos_descartadas")
  private String fotosDescartadas;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "borrador_foto_subida", joinColumns = @JoinColumn(name = "borrador_id"))
  @OrderBy("subidaEn ASC, id ASC")
  private List<FotoSubidaJpaEmbeddable> fotosSubidas = new ArrayList<>();

  @Column(nullable = false)
  private String estado;

  @Column(name = "producto_id")
  private UUID productoId;

  @Column(name = "motivo_rechazo")
  private String motivoRechazo;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected BorradorProductoJpaEntity() {}

  public BorradorProductoJpaEntity(
      UUID id,
      UUID publicacionId,
      UUID proveedorId,
      String extraccionCruda,
      String titulo,
      String linea,
      String tipo,
      BigDecimal precioProveedor,
      BigDecimal precioVentaSugerido,
      String tallasTipo,
      String tallasSirveHasta,
      String tallasValores,
      Integer cantidadTonos,
      String tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      String huella,
      String phash,
      String alertas,
      String fotosDescartadas,
      List<FotoSubidaJpaEmbeddable> fotosSubidas,
      String estado,
      UUID productoId,
      String motivoRechazo,
      Instant creadoEn,
      Instant actualizadoEn) {
    this.id = id;
    this.publicacionId = publicacionId;
    this.proveedorId = proveedorId;
    this.extraccionCruda = extraccionCruda;
    this.titulo = titulo;
    this.linea = linea;
    this.tipo = tipo;
    this.precioProveedor = precioProveedor;
    this.precioVentaSugerido = precioVentaSugerido;
    this.tallasTipo = tallasTipo;
    this.tallasSirveHasta = tallasSirveHasta;
    this.tallasValores = tallasValores;
    this.cantidadTonos = cantidadTonos;
    this.tonosNombrados = tonosNombrados;
    this.material = material;
    this.descripcion = descripcion;
    this.altEn = altEn;
    this.huella = huella;
    this.phash = phash;
    this.alertas = alertas;
    this.fotosDescartadas = fotosDescartadas;
    this.fotosSubidas = new ArrayList<>(fotosSubidas);
    this.estado = estado;
    this.productoId = productoId;
    this.motivoRechazo = motivoRechazo;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPublicacionId() {
    return publicacionId;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public String getExtraccionCruda() {
    return extraccionCruda;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getLinea() {
    return linea;
  }

  public String getTipo() {
    return tipo;
  }

  public BigDecimal getPrecioProveedor() {
    return precioProveedor;
  }

  public BigDecimal getPrecioVentaSugerido() {
    return precioVentaSugerido;
  }

  public String getTallasTipo() {
    return tallasTipo;
  }

  public String getTallasSirveHasta() {
    return tallasSirveHasta;
  }

  public String getTallasValores() {
    return tallasValores;
  }

  public Integer getCantidadTonos() {
    return cantidadTonos;
  }

  public String getTonosNombrados() {
    return tonosNombrados;
  }

  public String getMaterial() {
    return material;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public String getAltEn() {
    return altEn;
  }

  public String getHuella() {
    return huella;
  }

  public String getPhash() {
    return phash;
  }

  public String getFotosDescartadas() {
    return fotosDescartadas;
  }

  public List<FotoSubidaJpaEmbeddable> getFotosSubidas() {
    return fotosSubidas;
  }

  public String getAlertas() {
    return alertas;
  }

  public String getEstado() {
    return estado;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public String getMotivoRechazo() {
    return motivoRechazo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
