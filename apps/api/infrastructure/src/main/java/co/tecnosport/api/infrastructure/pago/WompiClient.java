package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Cliente de Wompi (docs/01-arquitectura.md), ambos métodos pura criptografía, sin llamada de red.
 *
 * <p>{@code generarFirmaIntegridad}: Wompi exige {@code SHA256(referencia + montoEnCentavos +
 * moneda + secretoIntegridad)}, hex — documentado por Wompi, no una suposición propia. El monto
 * siempre va en centavos aunque el peso colombiano no se fraccione en este dominio
 * (docs/02-modelo-datos.md): es un requisito del protocolo de Wompi, no del dominio de TecnoSport.
 *
 * <p>{@code verificarFirmaEvento}: el checksum de un webhook es {@code SHA256(concat(valores de
 * signature.properties, en orden) + timestamp + secretoEventos)} — verificado contra la
 * documentación pública de eventos de Wompi. Comparación insensible a mayúsculas y de tiempo
 * constante ({@link MessageDigest#isEqual}), para no filtrar por temporización cuánto del checksum
 * coincide.
 *
 * <p>Consultar una transacción (para la conciliación programada) llega a esta clase cuando se
 * construya ese caso de uso.
 */
public final class WompiClient implements PasarelaDePagos {

  private static final BigDecimal CENTAVOS_POR_PESO = BigDecimal.valueOf(100);

  private final String secretoIntegridad;
  private final String secretoEventos;

  public WompiClient(String secretoIntegridad, String secretoEventos) {
    if (secretoIntegridad == null || secretoIntegridad.isBlank()) {
      throw new IllegalArgumentException("El secreto de integridad de Wompi no puede estar vacío.");
    }
    if (secretoEventos == null || secretoEventos.isBlank()) {
      throw new IllegalArgumentException("El secreto de eventos de Wompi no puede estar vacío.");
    }
    this.secretoIntegridad = secretoIntegridad;
    this.secretoEventos = secretoEventos;
  }

  @Override
  public String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto) {
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");
    Objects.requireNonNull(monto, "El monto no puede ser nulo.");
    long montoEnCentavos = monto.valor().multiply(CENTAVOS_POR_PESO).longValueExact();
    String cadena = referencia.valor() + montoEnCentavos + Dinero.MONEDA + secretoIntegridad;
    return sha256Hex(cadena);
  }

  @Override
  public boolean verificarFirmaEvento(
      List<String> valoresPropiedades, long timestamp, String checksum) {
    Objects.requireNonNull(
        valoresPropiedades, "Los valores de las propiedades no pueden ser nulos.");
    Objects.requireNonNull(checksum, "El checksum no puede ser nulo.");
    String cadena = String.join("", valoresPropiedades) + timestamp + secretoEventos;
    String checksumCalculado = sha256Hex(cadena);
    return MessageDigest.isEqual(
        checksumCalculado.getBytes(StandardCharsets.UTF_8),
        checksum.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(String texto) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
    }
  }
}
