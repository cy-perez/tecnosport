package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "variante_atributo_valor")
public class VarianteAtributoValorJpaEntity {

  @Id private UUID id;

  @Column(name = "variante_id", nullable = false)
  private UUID varianteId;

  @Column(name = "atributo_id", nullable = false)
  private UUID atributoId;

  @Column(nullable = false)
  private String valor;

  @Column(name = "color_hex")
  private String colorHex;

  protected VarianteAtributoValorJpaEntity() {}

  public VarianteAtributoValorJpaEntity(
      UUID id, UUID varianteId, UUID atributoId, String valor, String colorHex) {
    this.id = id;
    this.varianteId = varianteId;
    this.atributoId = atributoId;
    this.valor = valor;
    this.colorHex = colorHex;
  }

  public UUID getId() {
    return id;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public UUID getAtributoId() {
    return atributoId;
  }

  public String getValor() {
    return valor;
  }

  public String getColorHex() {
    return colorHex;
  }
}
