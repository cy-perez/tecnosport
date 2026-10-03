package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "color_paleta")
public class ColorPaletaJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(name = "nombre_en", nullable = false)
  private String nombreEn;

  @Column(nullable = false)
  private String hex;

  @Column(nullable = false)
  private int orden;

  protected ColorPaletaJpaEntity() {}

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getNombreEn() {
    return nombreEn;
  }

  public String getHex() {
    return hex;
  }

  public int getOrden() {
    return orden;
  }
}
