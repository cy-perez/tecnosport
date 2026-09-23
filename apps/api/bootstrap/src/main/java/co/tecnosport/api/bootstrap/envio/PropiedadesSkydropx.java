package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code SKYDROPX_*} de docs/07-infra-gcp.md.
 *
 * <p>{@code urlBase} es configuración y no una constante con dos ambientes como en {@code
 * WompiClient}, y la razón no es que el host se desconozca: es que de los dos solo uno está
 * ejercido. El de <strong>pruebas</strong> es {@code sb-pro.skydropx.com}, y no por lectura sino
 * porque es el único de los candidatos que autentica con las credenciales del sandbox —{@code
 * api-pro} y {@code pro} responden {@code invalid_client}—; contra él se cotiza, se emitieron guías
 * reales y se verificó la firma del webhook (docs/13 §6). El de <strong>producción</strong> es
 * {@code api-pro.skydropx.com}, dicho por el bloque de credenciales de su propia documentación
 * (docs/13 §6.3). Las credenciales de producción ya están y no se estrenan hasta que exista la
 * infraestructura de producción, así que lo confirma la primera cotización real de ese día. En
 * {@code WompiClient} los dos hosts están documentados y ejercidos; aquí uno de los dos todavía no.
 *
 * <p>El tope y los intentos del sondeo son parámetros técnicos, no datos de negocio: la cotización
 * es asíncrona y hay que decidir cuánto se espera antes de darla por fallida.
 *
 * <p>{@code valorDeclaradoMinimo} y {@code valorDeclaradoMaximo} están aquí y no con lo del envío
 * porque <strong>el rango es de la plataforma</strong> —lo dicen sus dos {@code 422}, medidos en
 * docs/13 §6.4 y §6.13— y se iría con ella el día que cambiemos de proveedor. Quien los aplica no
 * la conoce: {@code ArmadorDeBultos} los recibe como pesos y ya ({@code adr/0035}, {@code
 * adr/0036}).
 */
@ConfigurationProperties(prefix = "tecnosport.skydropx")
public record PropiedadesSkydropx(
    String urlBase,
    String clientId,
    String clientSecret,
    int cotizacionTimeoutSegundos,
    int cotizacionIntentos,
    long valorDeclaradoMinimo,
    long valorDeclaradoMaximo) {

  public PropiedadesSkydropx {
    exigir(urlBase, "tecnosport.skydropx.url-base");
    exigir(clientId, "tecnosport.skydropx.client-id");
    exigir(clientSecret, "tecnosport.skydropx.client-secret");
    if (valorDeclaradoMinimo <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.valor-declarado-minimo debe ser mayor que cero.");
    }
    // Dos límites que se contradicen dejarían el checkout sin una sola tarifa posible, y eso se
    // descubriría en la primera cotización de un comprador real. Mejor no arrancar.
    if (valorDeclaradoMaximo <= valorDeclaradoMinimo) {
      throw new IllegalStateException(
          "tecnosport.skydropx.valor-declarado-maximo debe ser mayor que el mínimo.");
    }
    if (cotizacionTimeoutSegundos <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.cotizacion-timeout-segundos debe ser mayor que cero.");
    }
    if (cotizacionIntentos <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.cotizacion-intentos debe ser mayor que cero.");
    }
  }

  private static void exigir(String valor, String propiedad) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(propiedad + " no puede estar vacío.");
    }
  }
}
