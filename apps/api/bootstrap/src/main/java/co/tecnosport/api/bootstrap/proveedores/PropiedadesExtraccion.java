package co.tecnosport.api.bootstrap.proveedores;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tecnosport.proveedores.extraccion.*}: cómo se le habla al extractor.
 *
 * <p>{@code apiKey} es {@code ANTHROPIC_API_KEY} y vive en Secret Manager; <b>vacía, el extractor
 * es el sembrado</b> y el arranque lo dice en el registro, con el mismo criterio que la difusión:
 * sin clave no se habla con nadie, y se sabe. {@code modelo} es uno de los que la documentación
 * lista con salida estructurada (verificado el 30 de septiembre de 2026: {@code
 * claude-haiku-4-5-20251001}; {@code claude-sonnet-4-6} como alternativa). El umbral de confianza
 * no es un dato de negocio: es por debajo de qué cifra del propio extractor el borrador pide ojo
 * humano.
 */
@ConfigurationProperties(prefix = "tecnosport.proveedores.extraccion")
public record PropiedadesExtraccion(
    String urlBase,
    String apiKey,
    String modelo,
    int maxTokens,
    BigDecimal umbralConfianza,
    Duration timeout,
    int intentos,
    Duration esperaInicial,
    Duration ventanaAgrupacion) {

  public PropiedadesExtraccion {
    exigir(urlBase, "tecnosport.proveedores.extraccion.url-base");
    exigir(modelo, "tecnosport.proveedores.extraccion.modelo");
    if (maxTokens <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.extraccion.max-tokens debe ser mayor que cero.");
    }
    if (umbralConfianza == null
        || umbralConfianza.compareTo(BigDecimal.ZERO) < 0
        || umbralConfianza.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.extraccion.umbral-confianza va de 0 a 1.");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalStateException(
          "tecnosport.proveedores.extraccion.timeout debe ser una duración positiva.");
    }
    if (intentos <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.extraccion.intentos debe ser mayor que cero.");
    }
    if (esperaInicial == null || esperaInicial.isNegative()) {
      throw new IllegalStateException(
          "tecnosport.proveedores.extraccion.espera-inicial no puede ser negativa.");
    }
    if (ventanaAgrupacion == null || ventanaAgrupacion.isNegative() || ventanaAgrupacion.isZero()) {
      throw new IllegalStateException(
          "tecnosport.proveedores.extraccion.ventana-agrupacion debe ser una duración positiva.");
    }
  }

  public boolean tieneClave() {
    return apiKey != null && !apiKey.isBlank();
  }

  private static void exigir(String valor, String clave) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException("Falta " + clave + ".");
    }
  }
}
