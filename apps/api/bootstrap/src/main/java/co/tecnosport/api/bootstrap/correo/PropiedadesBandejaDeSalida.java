package co.tecnosport.api.bootstrap.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code intervaloMinutos} también se lee directamente en {@code TareaBandejaDeSalida.drenar} vía
 * {@code @Scheduled(fixedDelayString = "${...}")} — mismo patrón que {@code
 * PropiedadesPurgaCarritos}.
 *
 * <p><b>El tope de intentos y su espaciado no están aquí</b>, y es deliberado: son una constante
 * del mecanismo (ver {@code DrenarBandejaDeSalida}), como las 24 horas de {@code
 * RepositorioIdempotenciaJpa}. Lo que sí es una decisión del negocio, y por eso sí está, es cuántos
 * días se guarda el cuerpo de un correo ya enviado: lleva datos personales.
 */
@ConfigurationProperties(prefix = "tecnosport.correo.bandeja")
public record PropiedadesBandejaDeSalida(
    int intervaloMinutos, int retrasoInicialMinutos, int diasRetencion, int tamanoDelLote) {

  public PropiedadesBandejaDeSalida {
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.correo.bandeja.intervalo-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos < 0) {
      throw new IllegalStateException(
          "tecnosport.correo.bandeja.retraso-inicial-minutos no puede ser negativo.");
    }
    if (diasRetencion <= 0) {
      throw new IllegalStateException(
          "tecnosport.correo.bandeja.dias-retencion debe ser mayor que cero: con cero se borraría"
              + " el correo que la tarea acaba de mandar, y con él la constancia de que salió.");
    }
    if (tamanoDelLote <= 0) {
      throw new IllegalStateException(
          "tecnosport.correo.bandeja.tamano-del-lote debe ser mayor que cero.");
    }
  }
}
