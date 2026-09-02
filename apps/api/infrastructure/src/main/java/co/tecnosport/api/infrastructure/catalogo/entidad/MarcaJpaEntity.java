package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marca")
public class MarcaJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected MarcaJpaEntity() {}

  public MarcaJpaEntity(UUID id, String nombre, Instant creadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
