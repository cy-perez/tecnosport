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

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected CategoriaJpaEntity() {}

  public CategoriaJpaEntity(UUID id, String nombre, String slug, String linea, Instant creadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.slug = slug;
    this.linea = linea;
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

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
