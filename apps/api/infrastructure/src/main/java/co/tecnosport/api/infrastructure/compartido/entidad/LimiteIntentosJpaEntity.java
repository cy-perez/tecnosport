package co.tecnosport.api.infrastructure.compartido.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "limite_intentos")
public class LimiteIntentosJpaEntity {

  @Id private String clave;

  @Column(nullable = false)
  private int contador;

  @Column(name = "ventana_expira_en", nullable = false)
  private Instant ventanaExpiraEn;

  protected LimiteIntentosJpaEntity() {}

  public LimiteIntentosJpaEntity(String clave, int contador, Instant ventanaExpiraEn) {
    this.clave = clave;
    this.contador = contador;
    this.ventanaExpiraEn = ventanaExpiraEn;
  }

  public String getClave() {
    return clave;
  }

  public int getContador() {
    return contador;
  }

  public Instant getVentanaExpiraEn() {
    return ventanaExpiraEn;
  }
}
