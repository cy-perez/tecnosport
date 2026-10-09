package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "borrador_tecnologia")
public class BorradorTecnologiaJpaEntity {

  @Id private UUID id;

  @Column(name = "proveedor_id", nullable = false)
  private UUID proveedorId;

  @Column(name = "id_modelo", nullable = false)
  private String idModelo;

  @Column(nullable = false)
  private String huella;

  @Column(nullable = false)
  private String titulo;

  @Column(name = "marca_sugerida")
  private String marcaSugerida;

  @Column(name = "categoria_sugerida")
  private String categoriaSugerida;

  @Column(nullable = false)
  private String descripcion;

  @Column(name = "meta_descripcion")
  private String metaDescripcion;

  @Column private String paleta;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "borrador_tecnologia_configuracion",
      joinColumns = @JoinColumn(name = "borrador_id"))
  @OrderColumn(name = "orden")
  private List<ConfiguracionTecnologiaJpaEmbeddable> configuraciones = new ArrayList<>();

  @Column(name = "producto_id")
  private UUID productoId;

  @Column(nullable = false)
  private String estado;

  @Column(name = "motivo_rechazo")
  private String motivoRechazo;

  @Column(name = "visto_en", nullable = false)
  private Instant vistoEn;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected BorradorTecnologiaJpaEntity() {}

  public BorradorTecnologiaJpaEntity(
      UUID id,
      UUID proveedorId,
      String idModelo,
      String huella,
      String titulo,
      String marcaSugerida,
      String categoriaSugerida,
      String descripcion,
      String metaDescripcion,
      String paleta,
      List<ConfiguracionTecnologiaJpaEmbeddable> configuraciones,
      UUID productoId,
      String estado,
      String motivoRechazo,
      Instant vistoEn,
      Instant creadoEn,
      Instant actualizadoEn) {
    this.id = id;
    this.proveedorId = proveedorId;
    this.idModelo = idModelo;
    this.huella = huella;
    this.titulo = titulo;
    this.marcaSugerida = marcaSugerida;
    this.categoriaSugerida = categoriaSugerida;
    this.descripcion = descripcion;
    this.metaDescripcion = metaDescripcion;
    this.paleta = paleta;
    this.configuraciones = new ArrayList<>(configuraciones);
    this.productoId = productoId;
    this.estado = estado;
    this.motivoRechazo = motivoRechazo;
    this.vistoEn = vistoEn;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public String getIdModelo() {
    return idModelo;
  }

  public String getHuella() {
    return huella;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getMarcaSugerida() {
    return marcaSugerida;
  }

  public String getCategoriaSugerida() {
    return categoriaSugerida;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public String getMetaDescripcion() {
    return metaDescripcion;
  }

  public String getPaleta() {
    return paleta;
  }

  public List<ConfiguracionTecnologiaJpaEmbeddable> getConfiguraciones() {
    return configuraciones;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public String getEstado() {
    return estado;
  }

  public String getMotivoRechazo() {
    return motivoRechazo;
  }

  public Instant getVistoEn() {
    return vistoEn;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
