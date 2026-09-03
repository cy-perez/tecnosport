package co.tecnosport.api.presentation.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos de la cuenta para transferencia manual (docs/11-pagos-y-envios.md), de
 * docs/07-infra-gcp.md. Dato de negocio real, no inventado aquí: los valores por defecto son
 * placeholders que nunca sirven para transferir de verdad, iguales en criterio a los de Wompi en
 * {@code application.yml} — regla dura #5.
 */
@ConfigurationProperties(prefix = "tecnosport.transferencia-manual")
public record PropiedadesTransferenciaManual(
    String banco, String tipoCuenta, String numeroCuenta, String titular) {

  public PropiedadesTransferenciaManual {
    if (banco == null || banco.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.transferencia-manual.banco no puede estar vacío.");
    }
    if (tipoCuenta == null || tipoCuenta.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.transferencia-manual.tipo-cuenta no puede estar vacío.");
    }
    if (numeroCuenta == null || numeroCuenta.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.transferencia-manual.numero-cuenta no puede estar vacío.");
    }
    if (titular == null || titular.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.transferencia-manual.titular no puede estar vacío.");
    }
  }
}
