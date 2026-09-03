package co.tecnosport.api.infrastructure.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class UsuarioJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String correo;

  @Column(name = "clave_hash", nullable = false)
  private String claveHash;

  @Column(nullable = false)
  private String rol;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected UsuarioJpaEntity() {}

  public UsuarioJpaEntity(UUID id, String correo, String claveHash, String rol, Instant creadoEn) {
    this.id = id;
    this.correo = correo;
    this.claveHash = claveHash;
    this.rol = rol;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getCorreo() {
    return correo;
  }

  public String getClaveHash() {
    return claveHash;
  }

  public String getRol() {
    return rol;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
