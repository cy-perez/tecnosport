package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "producto")
public class ProductoJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private String descripcion;

  @Column(name = "marca_id", nullable = false)
  private UUID marcaId;

  @Column(name = "categoria_id", nullable = false)
  private UUID categoriaId;

  @Column(nullable = false)
  private String estado;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Column(nullable = false)
  private String origen;

  @Column(name = "proveedor_id")
  private UUID proveedorId;

  @Column(name = "precio_proveedor")
  private BigDecimal precioProveedor;

  @Column(name = "huella_proveedor")
  private String huellaProveedor;

  @Column(name = "visto_por_ultima_vez")
  private Instant vistoPorUltimaVez;

  @Column(name = "estado_disponibilidad", nullable = false)
  private String estadoDisponibilidad;

  /** Hasta qué talla le sirve una prenda de talla única, si el proveedor lo dijo (V75). */
  @Column(name = "talla_sirve_hasta")
  private String tallaSirveHasta;

  /** Si las fotos sin tono acompañan a las de cada color en la ficha (V79). */
  @Column(name = "fotos_generales_en_cada_color", nullable = false)
  private boolean fotosGeneralesEnCadaColor = true;

  protected ProductoJpaEntity() {}

  public ProductoJpaEntity(
      UUID id,
      String nombre,
      String slug,
      String descripcion,
      UUID marcaId,
      UUID categoriaId,
      String estado,
      Instant creadoEn,
      Instant actualizadoEn) {
    this(
        id,
        nombre,
        slug,
        descripcion,
        marcaId,
        categoriaId,
        estado,
        creadoEn,
        actualizadoEn,
        "MANUAL",
        null,
        null,
        null,
        null,
        "DISPONIBLE");
  }

  /** Con lo que un producto de proveedor lleva de más (V71). */
  public ProductoJpaEntity(
      UUID id,
      String nombre,
      String slug,
      String descripcion,
      UUID marcaId,
      UUID categoriaId,
      String estado,
      Instant creadoEn,
      Instant actualizadoEn,
      String origen,
      UUID proveedorId,
      BigDecimal precioProveedor,
      String huellaProveedor,
      Instant vistoPorUltimaVez,
      String estadoDisponibilidad) {
    this.origen = origen;
    this.proveedorId = proveedorId;
    this.precioProveedor = precioProveedor;
    this.huellaProveedor = huellaProveedor;
    this.vistoPorUltimaVez = vistoPorUltimaVez;
    this.estadoDisponibilidad = estadoDisponibilidad;
    this.id = id;
    this.nombre = nombre;
    this.slug = slug;
    this.descripcion = descripcion;
    this.marcaId = marcaId;
    this.categoriaId = categoriaId;
    this.estado = estado;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public UUID getMarcaId() {
    return marcaId;
  }

  public UUID getCategoriaId() {
    return categoriaId;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public String getOrigen() {
    return origen;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public BigDecimal getPrecioProveedor() {
    return precioProveedor;
  }

  public String getHuellaProveedor() {
    return huellaProveedor;
  }

  public Instant getVistoPorUltimaVez() {
    return vistoPorUltimaVez;
  }

  public String getTallaSirveHasta() {
    return tallaSirveHasta;
  }

  public ProductoJpaEntity conTallaSirveHasta(String tallaSirveHasta) {
    this.tallaSirveHasta = tallaSirveHasta;
    return this;
  }

  public boolean isFotosGeneralesEnCadaColor() {
    return fotosGeneralesEnCadaColor;
  }

  public ProductoJpaEntity conFotosGeneralesEnCadaColor(boolean fotosGeneralesEnCadaColor) {
    this.fotosGeneralesEnCadaColor = fotosGeneralesEnCadaColor;
    return this;
  }

  public String getEstadoDisponibilidad() {
    return estadoDisponibilidad;
  }
}
