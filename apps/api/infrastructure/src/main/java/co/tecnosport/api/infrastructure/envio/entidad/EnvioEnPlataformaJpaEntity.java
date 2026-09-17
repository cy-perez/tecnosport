package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Un identificador de envío de los que devolvió la plataforma, con su posición.
 *
 * <p>La posición es parte de la llave y no un campo más: en multienvío el orden de los envíos es el
 * orden de los bultos, y es lo que permite decir cuál guía corresponde a cuál paquete cuando
 * alguien tenga que mirar un fallo parcial.
 */
@Entity
@Table(name = "envio_en_plataforma")
@IdClass(EnvioEnPlataformaJpaEntity.Llave.class)
public class EnvioEnPlataformaJpaEntity {

  @Id
  @Column(name = "emision_id", nullable = false)
  private UUID emisionId;

  @Id
  @Column(nullable = false)
  private int posicion;

  @Column(name = "id_externo", nullable = false)
  private String idExterno;

  protected EnvioEnPlataformaJpaEntity() {}

  public EnvioEnPlataformaJpaEntity(UUID emisionId, int posicion, String idExterno) {
    this.emisionId = emisionId;
    this.posicion = posicion;
    this.idExterno = idExterno;
  }

  public UUID getEmisionId() {
    return emisionId;
  }

  public int getPosicion() {
    return posicion;
  }

  public String getIdExterno() {
    return idExterno;
  }

  /** Llave compuesta de JPA. Sin comportamiento: existe porque la especificación la pide. */
  public static class Llave implements Serializable {

    private UUID emisionId;
    private int posicion;

    public Llave() {}

    public Llave(UUID emisionId, int posicion) {
      this.emisionId = emisionId;
      this.posicion = posicion;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Llave otra
          && posicion == otra.posicion
          && Objects.equals(emisionId, otra.emisionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(emisionId, posicion);
    }
  }
}
