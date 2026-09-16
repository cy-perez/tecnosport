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

  protected GuiaEnvioJpaEntity() {}

  public GuiaEnvioJpaEntity(
      UUID id,
      UUID envioId,
      String transportadora,
      String codigoTransportadora,
      String numero,
      BigDecimal costoEnvio) {
    this.id = id;
    this.envioId = envioId;
    this.transportadora = transportadora;
    this.codigoTransportadora = codigoTransportadora;
    this.numero = numero;
    this.costoEnvio = costoEnvio;
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
}
