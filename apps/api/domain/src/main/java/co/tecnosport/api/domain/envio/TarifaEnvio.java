package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    return tarifas.stream().min(DE_LA_MAS_ECONOMICA);
  }

  /**
   * Una opción por transportadora —su tarifa más económica— y de la más económica a la más cara. Es
   * lo que el comprador elige desde el 8 de octubre de 2026 (ADR-0073): una transportadora, no un
   * servicio. Envía cotiza dos —mercancía y paquete terrestre— y ofrecerle las dos sería pedirle
   * que distinga algo que la plataforma no le explica.
   */
  public static List<TarifaEnvio> unaPorTransportadora(List<TarifaEnvio> tarifas) {
    Objects.requireNonNull(tarifas, "La lista de tarifas no puede ser nula.");
    Map<String, TarifaEnvio> porTransportadora = new LinkedHashMap<>();
    for (TarifaEnvio tarifa : tarifas.stream().sorted(DE_LA_MAS_ECONOMICA).toList()) {
      porTransportadora.putIfAbsent(clave(tarifa.transportadora()), tarifa);
    }
    return List.copyOf(porTransportadora.values());
  }

  /**
   * Si esta tarifa es de la transportadora que el comprador eligió. Por nombre, sin distinguir
   * mayúsculas: es lo único que el checkout conoce de la opción, y es a propósito — el {@code
   * idTarifa} no sale del servidor (adr/0021) y el costo lo vuelve a fijar quien crea el pedido.
   */
  public boolean esDe(String nombreDeTransportadora) {
    return nombreDeTransportadora != null
        && clave(transportadora).equals(clave(nombreDeTransportadora));
  }

  /** Empate a costo: gana la de menor plazo, no el orden en que el proveedor las devolvió. */
  private static final Comparator<TarifaEnvio> DE_LA_MAS_ECONOMICA =
      Comparator.comparing((TarifaEnvio t) -> t.costo().valor())
          .thenComparingInt(TarifaEnvio::diasEstimados);

  private static String clave(String nombre) {
    return nombre.trim().toLowerCase(Locale.ROOT);
  }

  private static void exigirTexto(String valor, String queEs) {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio(queEs + " no puede estar vacío.");
    }
  }
}
