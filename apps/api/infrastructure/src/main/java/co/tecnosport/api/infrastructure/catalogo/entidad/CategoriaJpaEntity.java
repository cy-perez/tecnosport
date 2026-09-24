package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categoria")
public class CategoriaJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private String linea;

  /**
   * El padre en el árbol, nulo en las categorías de primer nivel. Es el id y no una
   * {@code @ManyToOne} a propósito: nada de lo que hace el catálogo necesita navegar al padre desde
   * la entidad —el árbol se arma en memoria con la lista completa, que son treinta filas—, y una
   * relación aquí solo traería carga perezosa y proxies a un sitio que no los pide.
   */
  @Column(name = "padre_id")
  private UUID padreId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected CategoriaJpaEntity() {}

  public CategoriaJpaEntity(
      UUID id, String nombre, String slug, String linea, UUID padreId, Instant creadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.slug = slug;
    this.linea = linea;
    this.padreId = padreId;
    this.creadoEn = creadoEn;
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

  public String getLinea() {
    return linea;
  }

  public UUID getPadreId() {
    return padreId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
