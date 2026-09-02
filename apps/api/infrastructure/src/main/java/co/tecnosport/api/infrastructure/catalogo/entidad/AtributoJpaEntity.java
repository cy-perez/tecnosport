package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "atributo")
public class AtributoJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(nullable = false)
  private String tipo;

  // EAGER a propósito: es una lista chica de String, no una relación entre
  // entidades, y el mapeador siempre la necesita al reconstruir el Atributo.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "atributo_valor_permitido",
      joinColumns = @JoinColumn(name = "atributo_id"))
  @Column(name = "valor")
  private List<String> valoresPermitidos;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected AtributoJpaEntity() {}

  public AtributoJpaEntity(
      UUID id, String nombre, String tipo, List<String> valoresPermitidos, Instant creadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.tipo = tipo;
    this.valoresPermitidos = valoresPermitidos;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getTipo() {
    return tipo;
  }

  public List<String> getValoresPermitidos() {
    return valoresPermitidos;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
