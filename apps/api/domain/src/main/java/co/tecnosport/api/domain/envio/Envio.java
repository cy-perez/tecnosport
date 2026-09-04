package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transportadora, guía y costo real del despacho (docs/02-modelo-datos.md). Nace en el despacho, no
 * antes. El recaudo de contraentrega (comisión de la transportadora, fecha de conciliación) se
 * registra aparte, con {@link #conciliarRecaudo}: la comisión es un costo real, separado del flete,
 * para que el margen del pedido sea verdadero (docs/11-pagos-y-envios.md).
 */
public final class Envio {

  private final UUID id;
  private final UUID pedidoId;
  private final String transportadora;
  private final String guia;
  private final Dinero costoEnvio;
  private final Instant despachadoEn;
  private Dinero comisionRecaudo;
  private Instant recaudoConciliadoEn;

  public Envio(
      UUID id,
      UUID pedidoId,
      String transportadora,
      String guia,
      Dinero costoEnvio,
      Instant despachadoEn,
      Dinero comisionRecaudo,
      Instant recaudoConciliadoEn) {
    this.id = Objects.requireNonNull(id, "El id del envío no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    if (transportadora == null || transportadora.isBlank()) {
      throw new ExcepcionDeDominio("La transportadora no puede estar vacía.");
    }
    this.transportadora = transportadora;
    if (guia == null || guia.isBlank()) {
      throw new ExcepcionDeDominio("La guía no puede estar vacía.");
    }
    this.guia = guia;
    this.costoEnvio = Objects.requireNonNull(costoEnvio, "El costo de envío no puede ser nulo.");
    this.despachadoEn =
        Objects.requireNonNull(despachadoEn, "La fecha de despacho no puede ser nula.");
    this.comisionRecaudo = comisionRecaudo;
    this.recaudoConciliadoEn = recaudoConciliadoEn;
  }

  public static Envio crear(
      UUID pedidoId, String transportadora, String guia, Dinero costoEnvio, Instant ahora) {
    return new Envio(
        GeneradorIdentificador.nuevo(),
        pedidoId,
        transportadora,
        guia,
        costoEnvio,
        ahora,
        null,
        null);
  }

  /** Idempotente por diseño: un envío ya conciliado rechaza un segundo intento. */
  public void conciliarRecaudo(Dinero comisionRecaudo, Instant ahora) {
    Objects.requireNonNull(comisionRecaudo, "La comisión de recaudo no puede ser nula.");
    Objects.requireNonNull(ahora, "La fecha de conciliación no puede ser nula.");
    if (recaudoConciliadoEn != null) {
      throw new ExcepcionDeDominio("El recaudo de este envío ya fue conciliado.");
    }
    this.comisionRecaudo = comisionRecaudo;
    this.recaudoConciliadoEn = ahora;
  }

  public UUID id() {
    return id;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public String transportadora() {
    return transportadora;
  }

  public String guia() {
    return guia;
  }

  public Dinero costoEnvio() {
    return costoEnvio;
  }

  public Instant despachadoEn() {
    return despachadoEn;
  }

  public Optional<Dinero> comisionRecaudo() {
    return Optional.ofNullable(comisionRecaudo);
  }

  public Optional<Instant> recaudoConciliadoEn() {
    return Optional.ofNullable(recaudoConciliadoEn);
  }
}
