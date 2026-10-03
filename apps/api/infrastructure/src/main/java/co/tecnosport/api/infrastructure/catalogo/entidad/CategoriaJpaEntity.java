package co.tecnosport.api.infrastructure.catalogo.entidad;

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
import java.util.List;
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

  /** Las tallas de la categoría, una por línea; nula si usa las de su rama (V75). */
  @Column(name = "escala_tallas")
  private String escalaTallas;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  /**
   * Las etiquetas de redes. {@code EAGER} por lo mismo que {@code valoresPermitidos} en {@link
   * AtributoJpaEntity}: son treinta y cinco filas de categoría en total y el mapeador las necesita
   * siempre que reconstruye una, así que la carga perezosa solo traería un N+1 y una {@code
   * LazyInitializationException} esperando a que alguien mapee fuera de la transacción.
   *
   * <p>{@code @OrderColumn} porque el orden en que se escriben es el orden en que se publican.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "categoria_hashtag", joinColumns = @JoinColumn(name = "categoria_id"))
  @OrderColumn(name = "orden")
  @Column(name = "valor", nullable = false)
  private List<String> hashtags;

  protected CategoriaJpaEntity() {}

  /** El de siempre, sin etiquetas: lo llaman el sembrador y las pruebas que no hablan de redes. */
  public CategoriaJpaEntity(
      UUID id, String nombre, String slug, String linea, UUID padreId, Instant creadoEn) {
    this(id, nombre, slug, linea, padreId, creadoEn, List.of());
  }

  public CategoriaJpaEntity(
      UUID id,
      String nombre,
      String slug,
      String linea,
      UUID padreId,
      Instant creadoEn,
      List<String> hashtags) {
    this.id = id;
    this.nombre = nombre;
    this.slug = slug;
    this.linea = linea;
    this.padreId = padreId;
    this.creadoEn = creadoEn;
    this.hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
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

  public String getEscalaTallas() {
    return escalaTallas;
  }

  public void setEscalaTallas(String escalaTallas) {
    this.escalaTallas = escalaTallas;
  }

  /** Nunca nula: una categoría sin etiquetas tiene la lista vacía, no un nulo. */
  public List<String> getHashtags() {
    return hashtags == null ? List.of() : hashtags;
  }
}
