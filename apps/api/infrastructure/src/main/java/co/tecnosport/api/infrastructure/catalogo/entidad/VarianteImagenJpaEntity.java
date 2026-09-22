package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Una resolución publicada de una imagen. Sin relación JPA con {@link ImagenProductoJpaEntity}: en
 * este backend las entidades se referencian por UUID y se cargan en lote desde el mapeador, que es
 * lo que evita el N+1 de una colección EAGER y las sorpresas de una LAZY leída fuera de sesión.
 *
 * <p>El borrado sí es en cascada, pero de la base de datos ({@code on delete cascade} en la V60) y
 * no de Hibernate: una fila de imagen se borra desde tres sitios distintos y bastaría con que uno
 * olvidara las variantes.
 */
@Entity
@Table(name = "variante_imagen")
public class VarianteImagenJpaEntity {

  @Id private UUID id;

  @Column(name = "imagen_id", nullable = false)
  private UUID imagenId;

  @Column(nullable = false)
  private int ancho;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private long bytes;

  protected VarianteImagenJpaEntity() {}

  public VarianteImagenJpaEntity(UUID id, UUID imagenId, int ancho, String url, long bytes) {
    this.id = id;
    this.imagenId = imagenId;
    this.ancho = ancho;
    this.url = url;
    this.bytes = bytes;
  }

  public UUID getId() {
    return id;
  }

  public UUID getImagenId() {
    return imagenId;
  }

  public int getAncho() {
    return ancho;
  }

  public String getUrl() {
    return url;
  }

  public long getBytes() {
    return bytes;
  }
}
