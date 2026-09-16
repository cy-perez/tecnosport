package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "guia_envio")
public class GuiaEnvioJpaEntity {

  @Id private UUID id;

  @Column(name = "envio_id", nullable = false)
  private UUID envioId;

  @Column(nullable = false)
  private String transportadora;

  /** El nombre de la transportadora en Skydropx, no el visible. Vacío en las guías a mano. */
  @Column(name = "codigo_transportadora")
  private String codigoTransportadora;

  @Column(nullable = false)
  private String numero;

  @Column(name = "costo_envio", nullable = false)
  private BigDecimal costoEnvio;

  /**
   * El rótulo que devolvió la plataforma. Nulo en las guías tecleadas a mano y también en algunas
   * emitidas: dos guías de Servientrega por el mismo camino, una lo trajo y la otra no
   * (docs/13-skydropx-capacidades.md §6.7).
   */
  @Column(name = "url_etiqueta")
  private String urlEtiqueta;

  protected GuiaEnvioJpaEntity() {}

  public GuiaEnvioJpaEntity(
      UUID id,
      UUID envioId,
      String transportadora,
      String codigoTransportadora,
      String numero,
      BigDecimal costoEnvio,
      String urlEtiqueta) {
    this.id = id;
    this.envioId = envioId;
    this.transportadora = transportadora;
    this.codigoTransportadora = codigoTransportadora;
    this.numero = numero;
    this.costoEnvio = costoEnvio;
    this.urlEtiqueta = urlEtiqueta;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEnvioId() {
    return envioId;
  }

  public String getTransportadora() {
    return transportadora;
  }

  public String getCodigoTransportadora() {
    return codigoTransportadora;
  }

  public String getNumero() {
    return numero;
  }

  public BigDecimal getCostoEnvio() {
    return costoEnvio;
  }

  public String getUrlEtiqueta() {
    return urlEtiqueta;
  }
}
