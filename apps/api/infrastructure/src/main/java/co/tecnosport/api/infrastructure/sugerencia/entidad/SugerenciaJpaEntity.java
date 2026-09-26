package co.tecnosport.api.infrastructure.sugerencia.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Una fila del buzón. {@code correo} nulo es una sugerencia anónima, que es un caso válido. */
@Entity
@Table(name = "sugerencia")
public class SugerenciaJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, columnDefinition = "text")
  private String mensaje;

  @Column private String correo;

  @Column(name = "recibida_en", nullable = false)
  private Instant recibidaEn;

  protected SugerenciaJpaEntity() {}

  public SugerenciaJpaEntity(UUID id, String mensaje, String correo, Instant recibidaEn) {
    this.id = id;
    this.mensaje = mensaje;
    this.correo = correo;
    this.recibidaEn = recibidaEn;
  }

  public UUID getId() {
    return id;
  }

  public String getMensaje() {
    return mensaje;
  }

  public String getCorreo() {
    return correo;
  }

  public Instant getRecibidaEn() {
    return recibidaEn;
  }
}
