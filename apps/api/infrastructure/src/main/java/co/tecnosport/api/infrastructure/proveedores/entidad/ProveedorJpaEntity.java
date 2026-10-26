package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "proveedor")
public class ProveedorJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(nullable = false)
  private String linea;

  @Column(name = "telefono_whatsapp", nullable = false)
  private String telefonoWhatsapp;

  @Column(name = "nombre_en_exportacion", nullable = false)
  private String nombreEnExportacion;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "publicacion_automatica", nullable = false)
  private boolean publicacionAutomatica;

  @Column(name = "factor_de_margen")
  private BigDecimal factorDeMargen;

  @Column(name = "orden_de_publicacion", nullable = false)
  private String ordenDePublicacion;

  @Column(name = "dos_chats_en_un_zip", nullable = false)
  private boolean dosChatsEnUnZip;

  /** Si la prenda con su acompañante se publica como set (V113). */
  @Column(name = "arma_sets", nullable = false)
  private boolean armaSets;

  /** Si surte desde Medellín y lo suyo se puede recoger en el punto (V122). */
  @Column(name = "es_local", nullable = false)
  private boolean esLocal;

  /** Los ajustes de ingesta (V118): ver {@code AjustesDeIngesta}. */
  @Column(name = "una_foto_un_producto", nullable = false)
  private boolean unaFotoUnProducto;

  @Column(name = "venta_bajo_confirmacion", nullable = false)
  private boolean ventaBajoConfirmacion;

  @Column(name = "dias_de_vigencia")
  private Short diasDeVigencia;

  /** Separadas por coma; nulas con las otras dos si las dice cada anuncio. */
  @Column(name = "tallas_dama")
  private String tallasDama;

  @Column(name = "tallas_caballero")
  private String tallasCaballero;

  @Column(name = "ajuste_de_talla")
  private Short ajusteDeTalla;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "proveedor_tipo_excluido",
      joinColumns = @JoinColumn(name = "proveedor_id"))
  @Column(name = "tipo", nullable = false)
  private Set<String> tiposExcluidos = new HashSet<>();

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected ProveedorJpaEntity() {}

  public ProveedorJpaEntity(
      UUID id,
      String nombre,
      String linea,
      String telefonoWhatsapp,
      String nombreEnExportacion,
      boolean activo,
      boolean publicacionAutomatica,
      BigDecimal factorDeMargen,
      String ordenDePublicacion,
      boolean dosChatsEnUnZip,
      Set<String> tiposExcluidos,
      Instant creadoEn,
      Instant actualizadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.linea = linea;
    this.telefonoWhatsapp = telefonoWhatsapp;
    this.nombreEnExportacion = nombreEnExportacion;
    this.activo = activo;
    this.publicacionAutomatica = publicacionAutomatica;
    this.factorDeMargen = factorDeMargen;
    this.ordenDePublicacion = ordenDePublicacion;
    this.dosChatsEnUnZip = dosChatsEnUnZip;
    this.tiposExcluidos = new HashSet<>(tiposExcluidos);
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getLinea() {
    return linea;
  }

  public String getTelefonoWhatsapp() {
    return telefonoWhatsapp;
  }

  public String getNombreEnExportacion() {
    return nombreEnExportacion;
  }

  public boolean isActivo() {
    return activo;
  }

  public boolean isPublicacionAutomatica() {
    return publicacionAutomatica;
  }

  public BigDecimal getFactorDeMargen() {
    return factorDeMargen;
  }

  public String getOrdenDePublicacion() {
    return ordenDePublicacion;
  }

  public boolean isDosChatsEnUnZip() {
    return dosChatsEnUnZip;
  }

  public Set<String> getTiposExcluidos() {
    return tiposExcluidos;
  }

  public boolean isArmaSets() {
    return armaSets;
  }

  /** Fuera del constructor para no alargarlo más: lo pone el adaptador al armar la fila. */
  public ProveedorJpaEntity conArmaSets(boolean armaSets) {
    this.armaSets = armaSets;
    return this;
  }

  public boolean isEsLocal() {
    return esLocal;
  }

  /** Como {@link #conArmaSets}. */
  public ProveedorJpaEntity conEsLocal(boolean esLocal) {
    this.esLocal = esLocal;
    return this;
  }

  /** Como {@link #conArmaSets}: los ajustes de ingesta los pone el adaptador. */
  public ProveedorJpaEntity conAjustesDeIngesta(
      boolean unaFotoUnProducto,
      boolean ventaBajoConfirmacion,
      Short diasDeVigencia,
      String tallasDama,
      String tallasCaballero,
      Short ajusteDeTalla) {
    this.unaFotoUnProducto = unaFotoUnProducto;
    this.ventaBajoConfirmacion = ventaBajoConfirmacion;
    this.diasDeVigencia = diasDeVigencia;
    this.tallasDama = tallasDama;
    this.tallasCaballero = tallasCaballero;
    this.ajusteDeTalla = ajusteDeTalla;
    return this;
  }

  public boolean isUnaFotoUnProducto() {
    return unaFotoUnProducto;
  }

  public boolean isVentaBajoConfirmacion() {
    return ventaBajoConfirmacion;
  }

  public Short getDiasDeVigencia() {
    return diasDeVigencia;
  }

  public String getTallasDama() {
    return tallasDama;
  }

  public String getTallasCaballero() {
    return tallasCaballero;
  }

  public Short getAjusteDeTalla() {
    return ajusteDeTalla;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
