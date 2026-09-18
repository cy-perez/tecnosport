package co.tecnosport.api.bootstrap.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cada cuánto sale la tanda de comprobantes de compra.
 *
 * <p>Minutos, y pocos, al revés que {@code PropiedadesVigilanciaPlazoEntrega}: aquél vigila un
 * plazo de treinta días y puede permitirse medio día de retraso; éste le manda a alguien el soporte
 * de la compra que acaba de hacer. No hace falta que sea instantáneo —la pantalla de estado ya le
 * confirmó el pedido al volver del checkout— pero sí que llegue mientras todavía está pendiente de
 * su compra.
 *
 * <p>El retraso inicial tiene que ser menor que el intervalo por la misma razón de siempre: si lo
 * igualara, cada despliegue reiniciaría la cuenta.
 */
@ConfigurationProperties(prefix = "tecnosport.pedido.comprobantes")
public record PropiedadesComprobantes(int intervaloMinutos, int retrasoInicialMinutos) {

  public PropiedadesComprobantes {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.comprobantes.intervalo-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.comprobantes.retraso-inicial-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos >= intervaloMinutos) {
      throw new IllegalStateException(
          "tecnosport.pedido.comprobantes.retraso-inicial-minutos debe ser menor que el intervalo:"
              + " si lo iguala, cada despliegue reinicia la cuenta y la tarea puede no correr"
              + " nunca.");
    }
  }
}
