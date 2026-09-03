package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** El código DANE de la ciudad es la clave primaria: ya es único, sin id aparte que mantener. */
@Entity
@Table(name = "cobertura_contraentrega")
public class CoberturaContraentregaJpaEntity {

  @Id
  @Column(name = "codigo_dane_ciudad")
  private String codigoDaneCiudad;

  protected CoberturaContraentregaJpaEntity() {}

  public CoberturaContraentregaJpaEntity(String codigoDaneCiudad) {
    this.codigoDaneCiudad = codigoDaneCiudad;
  }

  public String getCodigoDaneCiudad() {
    return codigoDaneCiudad;
  }
}
