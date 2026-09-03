package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de Wompi (docs/01-arquitectura.md).
 *
 * <p>{@code generarFirmaIntegridad} y {@code verificarFirmaEvento} son pura criptografía, sin
 * llamada de red: {@code SHA256(referencia + montoEnCentavos + moneda + secretoIntegridad)} para la
 * firma de integridad, {@code SHA256(concat(valores de signature.properties, en orden) + timestamp
 * + secretoEventos)} para el checksum del webhook — ambos documentados por Wompi, no una suposición
 * propia. El monto siempre va en centavos aunque el peso colombiano no se fraccione en este dominio
 * (docs/02-modelo-datos.md): es un requisito del protocolo de Wompi. Comparación de checksum
 * insensible a mayúsculas y de tiempo constante ({@link MessageDigest#isEqual}).
 *
 * <p>{@code consultarTransaccion} sí llama a la API real: {@code GET /transactions/{id}} con {@code
 * Authorization: Bearer <llave pública>} — verificado por búsqueda, no inventado (regla dura #9).
 * Las URL base de sandbox y producción son las que Wompi documenta para cada ambiente; cualquier
 * valor de {@code ambiente} que no sea exactamente {@code "produccion"} usa sandbox, para que un
 * valor de configuración mal escrito nunca apunte por accidente a producción.
 */
public final class WompiClient implements PasarelaDePagos {

  private static final String URL_BASE_SANDBOX = "https://sandbox.wompi.co/v1";
  private static final String URL_BASE_PRODUCCION = "https://production.wompi.co/v1";
  private static final BigDecimal CENTAVOS_POR_PESO = BigDecimal.valueOf(100);
  private static final Duration TIMEOUT_CONSULTA = Duration.ofSeconds(10);

  private final String secretoIntegridad;
  private final String secretoEventos;
  private final String llavePublica;
  private final String urlBase;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  public WompiClient(
      String secretoIntegridad, String secretoEventos, String llavePublica, String ambiente) {
    this(
        secretoIntegridad,
        secretoEventos,
        llavePublica,
        URI.create("produccion".equals(ambiente) ? URL_BASE_PRODUCCION : URL_BASE_SANDBOX));
  }

  /**
   * Punto de extensión para pruebas: apunta a un servidor de prueba en vez de la URL real de Wompi.
   */
  WompiClient(String secretoIntegridad, String secretoEventos, String llavePublica, URI urlBase) {
    if (secretoIntegridad == null || secretoIntegridad.isBlank()) {
      throw new IllegalArgumentException("El secreto de integridad de Wompi no puede estar vacío.");
    }
    if (secretoEventos == null || secretoEventos.isBlank()) {
      throw new IllegalArgumentException("El secreto de eventos de Wompi no puede estar vacío.");
    }
    if (llavePublica == null || llavePublica.isBlank()) {
      throw new IllegalArgumentException("La llave pública de Wompi no puede estar vacía.");
    }
    this.secretoIntegridad = secretoIntegridad;
    this.secretoEventos = secretoEventos;
    this.llavePublica = llavePublica;
    this.urlBase = urlBase.toString();
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

  @Override
  public Optional<String> consultarTransaccion(String idTransaccionWompi) {
    Objects.requireNonNull(idTransaccionWompi, "El id de transacción no puede ser nulo.");
    try {
      HttpRequest peticion =
          HttpRequest.newBuilder()
              .uri(URI.create(urlBase + "/transactions/" + idTransaccionWompi))
              .timeout(TIMEOUT_CONSULTA)
              .header("Authorization", "Bearer " + llavePublica)
              .GET()
              .build();
      HttpResponse<String> respuesta =
          httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
      if (respuesta.statusCode() != 200) {
        return Optional.empty();
      }
      JsonNode raiz = json.readTree(respuesta.body());
      String estado = raiz.path("data").path("status").asString();
      return estado.isBlank() ? Optional.empty() : Optional.of(estado);
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (JacksonException e) {
      // Un cuerpo de Wompi que no se puede parsear es, para la conciliación, lo mismo que no
      // poder consultar: se reintenta en la próxima corrida, no es un error de negocio.
      return Optional.empty();
    }
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
