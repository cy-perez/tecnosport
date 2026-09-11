package co.tecnosport.api.bootstrap.pedido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cada cuánto mira el vigilante si algún pedido pasó de los treinta días para entregarse.
 *
 * <p>Las dos llaves se leen también directamente en {@code TareaAvisoDePlazoDeEntrega.vigilar} vía
 * {@code @Scheduled(fixedDelayString = "${...}")} — Spring resuelve ese placeholder contra el
 * Environment, no contra este bean; los dos leen lo mismo de {@code application.yml}, con el mismo
 * efecto. Mismo arreglo que {@code PropiedadesConciliacionWompi}.
 *
 * <p>En minutos y no en horas por lo mismo que la conciliación de Wompi: {@code @Scheduled} acepta
 * una sola unidad para el intervalo y para el retraso inicial, y aquí hacen falta dos escalas muy
 * distintas.
 *
 * <p>Doce horas de intervalo, y no quince minutos como Wompi: el plazo que vigila es de treinta
 * días calendario, así que enterarse medio día más tarde no le cuesta nada a nadie. El retraso
 * inicial, en cambio, es corto a propósito — si igualara al intervalo, cada despliegue reiniciaría
 * la cuenta y con despliegues diarios la tarea no correría nunca.
 */
@ConfigurationProperties(prefix = "tecnosport.pedido.vigilancia-plazo")
public record PropiedadesVigilanciaPlazoEntrega(int intervaloMinutos, int retrasoInicialMinutos) {

  public PropiedadesVigilanciaPlazoEntrega {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.vigilancia-plazo.intervalo-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.pedido.vigilancia-plazo.retraso-inicial-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos >= intervaloMinutos) {
      throw new IllegalStateException(
          "tecnosport.pedido.vigilancia-plazo.retraso-inicial-minutos debe ser menor que el"
              + " intervalo: si lo iguala, cada despliegue reinicia la cuenta y la tarea puede no"
              + " correr nunca.");
    }
  }
}
