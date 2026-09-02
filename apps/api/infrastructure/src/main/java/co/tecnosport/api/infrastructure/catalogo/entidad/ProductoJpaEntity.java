package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
