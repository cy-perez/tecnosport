package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Un cobro que la transportadora aplicó <em>después</em> de emitir la guía. El caso típico es el
 * sobrepeso: se declaró un kilo, el paquete pesó tres, y la diferencia se cobra semanas más tarde
 * contra el saldo de la cuenta. Es el riesgo que {@code docs/02-modelo-datos.md} nombra al prohibir
 * inventar pesos, y hasta hoy era invisible: el flete que el pedido registra es el de la tarifa, no
 * el que se acabó pagando.
 *
 * <p><strong>No es un tipo del dominio y no entra en el margen del pedido todavía.</strong> Lo que
 * se hace con esto es avisar; contabilizarlo exige decidir si el sobrecosto vive en la guía o en el
 * envío —el cargo es por envío, la discrepancia de peso es por paquete— y un endpoint que lo
 * devuelva, y eso va con el panel administrativo ({@code docs/11-pagos-y-envios.md}).
 *
 * <p><strong>{@code monto} es pesos porque la cuenta es en pesos, no porque el proveedor lo
 * diga.</strong> La respuesta no trae moneda: trae el monto como texto ({@code "15.50"}) y nada
 * más. Lo que sí está medido es que el cobro se descuenta del crédito de la cuenta y que ese
 * crédito está en COP ({@code GET /finance/credits} responde {@code currency: "COP"}). Quien mueva
 * esto a otra cuenta tiene que volver a mirar esa suposición, y por eso está escrita aquí.
 */
public record SobrecostoDeEnvio(
    String envioEnPlataforma,
    String guia,
    String transportadora,
    String tipo,
    Dinero monto,
    Instant detectadoEn) {

  public SobrecostoDeEnvio {
    exigirTexto(envioEnPlataforma, "El envío del sobrecosto");
    exigirTexto(tipo, "El tipo del sobrecosto");
    Objects.requireNonNull(monto, "El monto del sobrecosto no puede ser nulo.");
  }

  /**
   * Cuándo lo detectó la plataforma, si lo dijo. Vacío es posible: el esquema declara {@code
   * detection_date} como nulable, y aunque en la práctica venga, dar por hecho lo contrario es la
   * clase de suposición que esta integración ya pagó cuatro veces.
   */
  public Optional<Instant> detectado() {
    return Optional.ofNullable(detectadoEn);
  }

  /**
   * La guía, si el cobro la trae. Un cobro sin guía se avisa igual: el monto ya se fue del saldo.
   */
  public Optional<String> numeroDeGuia() {
    return guia == null || guia.isBlank() ? Optional.empty() : Optional.of(guia);
  }

  public Optional<String> nombreDeTransportadora() {
    return transportadora == null || transportadora.isBlank()
        ? Optional.empty()
        : Optional.of(transportadora);
  }

  /**
   * Con qué se recuerda que de este cobro ya se avisó, y <strong>se compone porque la respuesta no
   * trae ningún identificador del cobro</strong> — lo verificado el 18 de septiembre de 2026 contra
   * el esquema del endpoint: hay {@code shipment_id} y {@code package_id}, los dos del envío, y
   * ninguno del cargo.
   *
   * <p>Lleva el monto a propósito. Si la transportadora reliquida el mismo cargo por otra cifra, la
   * clave cambia y se avisa otra vez; el error en ese lado es enterarse dos veces de algo de
   * dinero, y en el otro es no enterarse del cambio. {@code status} no entra, que es la otra cara:
   * pasar de {@code pending_payment} a pagado no es una novedad que nadie tenga que mirar.
   */
  public String clave() {
    return String.join(
        "|",
        envioEnPlataforma,
        tipo,
        monto.valor().toPlainString(),
        detectado().map(Instant::toString).orElse("sin-fecha"));
  }

  private static void exigirTexto(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(queEs + " no puede estar vacío.");
    }
  }
}
