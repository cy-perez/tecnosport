package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Una tarifa cotizada para un destino y un paquete concretos. Es lo que el cotizador devuelve y lo
 * que el pedido congela si el comprador sigue adelante (adr/0021).
 *
 * <p>Es un tipo del dominio, no un DTO del proveedor: {@code idTarifa} es opaco a propósito —lo
 * emite quien cotiza y solo sirve para emitir la guía después— y nada aquí conoce a Skydropx.
 *
 * <p>{@code venceEn} existe porque una tarifa no vale para siempre: las de Skydropx valen 24 horas.
 * Cobrar un flete con una tarifa vencida es despachar a un precio que ya no existe.
 */
public record TarifaEnvio(
    String idTarifa,
    String transportadora,
    String servicio,
    Dinero costo,
    int diasEstimados,
    boolean admiteContraentrega,
    Instant venceEn) {

  public TarifaEnvio {
    exigirTexto(idTarifa, "El identificador de la tarifa");
    exigirTexto(transportadora, "La transportadora de la tarifa");
    exigirTexto(servicio, "El servicio de la tarifa");
    Objects.requireNonNull(costo, "El costo de la tarifa no puede ser nulo.");
    if (costo.valor().signum() < 0) {
      throw new ExcepcionDeDominio("El costo de una tarifa no puede ser negativo: " + costo);
    }
    if (diasEstimados < 0) {
      throw new ExcepcionDeDominio(
          "El plazo estimado de una tarifa no puede ser negativo: " + diasEstimados);
    }
    Objects.requireNonNull(venceEn, "El vencimiento de la tarifa no puede ser nulo.");
  }

  public boolean estaVigente(Instant ahora) {
    Objects.requireNonNull(ahora, "El instante no puede ser nulo.");
    return ahora.isBefore(venceEn);
  }

  /**
   * La regla de adr/0021: de todas las tarifas se toma la más económica, y la elige el servidor —
   * no el comprador y no el proveedor. Vive aquí y no en el adaptador porque es una decisión de
   * negocio: el día que se ofrezca "más rápido por más plata", se cambia en un solo sitio.
   *
   * <p>Empate a costo: gana la de menor plazo. Sin ese desempate, dos tarifas iguales en precio se
   * ordenarían según el orden en que llegaron, que es el capricho del proveedor.
   */
  public static Optional<TarifaEnvio> masEconomica(List<TarifaEnvio> tarifas) {
    Objects.requireNonNull(tarifas, "La lista de tarifas no puede ser nula.");
    return tarifas.stream()
        .min(
            Comparator.comparing((TarifaEnvio t) -> t.costo().valor())
                .thenComparingInt(TarifaEnvio::diasEstimados));
  }

  private static void exigirTexto(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio(queEs + " no puede estar vacío.");
    }
  }
}
