package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensaje_proveedor")
public class MensajeProveedorJpaEntity {

  @Id private UUID id;

  @Column(name = "proveedor_id", nullable = false)
  private UUID proveedorId;

  @Column(name = "lote_id", nullable = false)
  private UUID loteId;

  @Column(name = "id_externo", nullable = false)
  private String idExterno;

  @Column(name = "enviado_en", nullable = false)
  private Instant enviadoEn;

  @Column(nullable = false)
  private String tipo;

  @Column private String texto;

  @Column(name = "pie_de_foto")
  private String pieDeFoto;

  @Column(name = "referencia_archivo")
  private String referenciaArchivo;

  @Column(name = "medio_omitido", nullable = false)
  private boolean medioOmitido;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  /** El lugar en la lista con la que se guardó; desempata los mensajes del mismo instante. */
  @Column(nullable = false)
  private int posicion;

  protected MensajeProveedorJpaEntity() {}

  public MensajeProveedorJpaEntity(
      UUID id,
      UUID proveedorId,
      UUID loteId,
      String idExterno,
      Instant enviadoEn,
      String tipo,
      String texto,
      String pieDeFoto,
      String referenciaArchivo,
      boolean medioOmitido,
      Instant creadoEn,
      int posicion) {
    this.id = id;
    this.proveedorId = proveedorId;
    this.loteId = loteId;
    this.idExterno = idExterno;
    this.enviadoEn = enviadoEn;
    this.tipo = tipo;
    this.texto = texto;
    this.pieDeFoto = pieDeFoto;
    this.referenciaArchivo = referenciaArchivo;
    this.medioOmitido = medioOmitido;
    this.creadoEn = creadoEn;
    this.posicion = posicion;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public UUID getLoteId() {
    return loteId;
  }

  public String getIdExterno() {
    return idExterno;
  }

  public Instant getEnviadoEn() {
    return enviadoEn;
  }

  public String getTipo() {
    return tipo;
  }

  public String getTexto() {
    return texto;
  }

  public String getPieDeFoto() {
    return pieDeFoto;
  }

  public String getReferenciaArchivo() {
    return referenciaArchivo;
  }

  public boolean isMedioOmitido() {
    return medioOmitido;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public int getPosicion() {
    return posicion;
  }
}
