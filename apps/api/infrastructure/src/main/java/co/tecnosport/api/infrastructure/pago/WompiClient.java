package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Cliente de Wompi (docs/01-arquitectura.md). {@code generarFirmaIntegridad} es pura criptografía,
 * sin llamada de red: Wompi exige {@code SHA256(referencia + montoEnCentavos + moneda +
 * secretoIntegridad)}, hex en minúsculas — documentado por Wompi, no una suposición propia. El
 * monto siempre va en centavos aunque el peso colombiano no se fraccione en este dominio
 * (docs/02-modelo-datos.md): es un requisito del protocolo de Wompi, no del dominio de TecnoSport.
 * Consultar una transacción (para la conciliación programada) y verificar la firma de un webhook
 * llegan a esta clase cuando se construyan esos casos de uso.
 */
public final class WompiClient implements PasarelaDePagos {

  private static final BigDecimal CENTAVOS_POR_PESO = BigDecimal.valueOf(100);

  private final String secretoIntegridad;

  public WompiClient(String secretoIntegridad) {
    if (secretoIntegridad == null || secretoIntegridad.isBlank()) {
      throw new IllegalArgumentException("El secreto de integridad de Wompi no puede estar vacío.");
    }
    this.secretoIntegridad = secretoIntegridad;
  }

  @Override
  public String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto) {
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");
    Objects.requireNonNull(monto, "El monto no puede ser nulo.");
    long montoEnCentavos = monto.valor().multiply(CENTAVOS_POR_PESO).longValueExact();
    String cadena = referencia.valor() + montoEnCentavos + Dinero.MONEDA + secretoIntegridad;
    return sha256Hex(cadena);
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
