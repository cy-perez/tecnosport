package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Un mensaje dentro de una publicación, con su papel ({@code TEXTO} o {@code MEDIO}) y su orden.
 */
@Entity
@Table(name = "publicacion_mensaje")
public class PublicacionMensajeJpaEntity {

  @EmbeddedId private Clave clave;

  @Column(name = "mensaje_id", nullable = false)
  private UUID mensajeId;

  protected PublicacionMensajeJpaEntity() {}

  public PublicacionMensajeJpaEntity(UUID publicacionId, String rol, int orden, UUID mensajeId) {
    this.clave = new Clave(publicacionId, rol, orden);
    this.mensajeId = mensajeId;
  }

  public Clave getClave() {
    return clave;
  }

  public UUID getMensajeId() {
    return mensajeId;
  }

  @Embeddable
  public static class Clave implements Serializable {

    @Column(name = "publicacion_id", nullable = false)
    private UUID publicacionId;

    @Column(nullable = false)
    private String rol;

    @Column(nullable = false)
    private int orden;

    protected Clave() {}

    public Clave(UUID publicacionId, String rol, int orden) {
      this.publicacionId = publicacionId;
      this.rol = rol;
      this.orden = orden;
    }

    public UUID getPublicacionId() {
      return publicacionId;
    }

    public String getRol() {
      return rol;
    }

    public int getOrden() {
      return orden;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Clave otra
          && publicacionId.equals(otra.publicacionId)
          && rol.equals(otra.rol)
          && orden == otra.orden;
    }

    @Override
    public int hashCode() {
      return Objects.hash(publicacionId, rol, orden);
    }
  }
}
