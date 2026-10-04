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

  /** MULTICOLOR, ESTAMPADO o ANIMAL_PRINT; nulo en un color liso (V80). */
  @Column private String patron;

  /** Los colores del patrón en su orden, separados por coma; nulo en un color liso. */
  @Column(name = "colores_patron")
  private String coloresPatron;

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

  public String getPatron() {
    return patron;
  }

  public String getColoresPatron() {
    return coloresPatron;
  }
}
