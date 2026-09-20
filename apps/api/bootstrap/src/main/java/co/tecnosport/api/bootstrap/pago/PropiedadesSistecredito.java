package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la pasarela de Sistecrédito ({@code adr/0048}, docs/07-infra-gcp.md).
 *
 * <p>Todo entra por variable de entorno, incluida la URL base: la guía {@code G-ALI-08} publica
 * {@code https://api.credinet.co/pay}, pero un endpoint literal en el código va contra la regla
 * dura #5 y además impide apuntar las pruebas a otro sitio.
 *
 * <p><b>{@code sandboxActivo} no es una comodidad de desarrollo: es un freno de seguridad.</b> Esta
 * cuenta solo tiene credenciales productivas, así que este booleano es lo único que separa una
 * prueba de un crédito real a nombre de una persona. Por eso exige decir qué estado simula —un
 * sandbox encendido sin estado sería una simulación de nada— y por eso {@code
 * ConfiguracionSistecredito} se niega a arrancar si está encendido en un despliegue que mueve
 * dinero de verdad.
 */
@ConfigurationProperties(prefix = "tecnosport.sistecredito")
public record PropiedadesSistecredito(
    boolean habilitado,
    String urlBase,
    String llaveSuscripcion,
    String storeId,
    String vendorId,
    String ambiente,
    String urlRespuesta,
    String urlConfirmacion,
    int metodoDePagoId,
    int timeoutSegundos,
    int sondeoIntentos,
    long sondeoEsperaMilis,
    boolean sandboxActivo,
    String sandboxEstado,
    BigDecimal montoMinimo) {

  /** Los que la pasarela admite simular (guía {@code G-ALI-08}, "Modo sandbox"). */
  private static final Set<String> ESTADOS_SIMULABLES =
      Set.of("PendingForPaymentMethod", "Rejected", "Pending", "Approved", "Cancelled");

  private static final Set<String> AMBIENTES = Set.of("Staging", "Production");

  public PropiedadesSistecredito {
    if (habilitado) {
      exigir(urlBase, "tecnosport.sistecredito.url-base");
      exigir(llaveSuscripcion, "tecnosport.sistecredito.llave-suscripcion");
      exigir(storeId, "tecnosport.sistecredito.store-id");
      exigir(vendorId, "tecnosport.sistecredito.vendor-id");
      exigir(ambiente, "tecnosport.sistecredito.ambiente");
      exigir(urlRespuesta, "tecnosport.sistecredito.url-respuesta");
      exigir(urlConfirmacion, "tecnosport.sistecredito.url-confirmacion");
      if (!AMBIENTES.contains(ambiente)) {
        throw new IllegalStateException(
            "tecnosport.sistecredito.ambiente tiene que ser Staging o Production, no "
                + ambiente
                + ": es el header SCOrigen y la pasarela lo compara tal cual.");
      }
      if (metodoDePagoId <= 0) {
        throw new IllegalStateException(
            "tecnosport.sistecredito.metodo-de-pago-id no puede estar sin definir.");
      }
      // El mínimo del crédito lo define Sistecrédito y no es público (adr/0048). Sin él, el
      // checkout ofrecería un método que la pasarela va a rechazar con su código 802.
      if (montoMinimo == null || montoMinimo.signum() <= 0) {
        throw new IllegalStateException(
            "tecnosport.sistecredito.monto-minimo hace falta para habilitar Sistecrédito:"
                + " es el mínimo del crédito y lo define Sistecrédito, no nosotros.");
      }
    }
    if (sandboxActivo && !ESTADOS_SIMULABLES.contains(sandboxEstado)) {
      throw new IllegalStateException(
          "tecnosport.sistecredito.sandbox-estado tiene que ser uno de "
              + ESTADOS_SIMULABLES
              + " cuando el modo sandbox está encendido, no "
              + sandboxEstado
              + ".");
    }
  }

  /** {@code null} cuando no está configurado, que es legítimo mientras el método esté apagado. */
  public Dinero montoMinimoComoDinero() {
    return montoMinimo == null ? null : Dinero.deCop(montoMinimo);
  }

  private static void exigir(String valor, String propiedad) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(
          propiedad + " hace falta para habilitar Sistecrédito y está vacía.");
    }
  }
}
