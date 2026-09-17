package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El vigilante de la bandeja de revisión: cada cuánto mira, cuánto tiempo puede quedarse algo sin
 * que nadie lo toque, y a quién se le avisa.
 *
 * <p><strong>{@code horas-umbral} sí es un dato de negocio</strong>, a diferencia del intervalo:
 * dice cuánto tiempo el negocio considera aceptable que un paquete detenido —o una emisión con
 * saldo comprometido— siga sin que nadie lo mire. Veinticuatro horas es la decisión tomada, y el
 * motivo es que el comprador de un pedido ya despachado espera movimiento diario: enterarse más
 * tarde que él es justo lo que la bandeja vino a evitar.
 *
 * <p>El intervalo, en cambio, es solo cada cuánto se mira. Seis horas contra un umbral de
 * veinticuatro: lo bastante seguido para que el aviso no llegue con medio día de retraso sobre el
 * umbral, y lo bastante espaciado para que una bandeja vacía no cueste nada.
 *
 * <p>El destinatario lleva valor por omisión real porque no es un secreto: es el correo del negocio
 * que ya está publicado en el pie del sitio y en los documentos legales.
 */
@ConfigurationProperties(prefix = "tecnosport.revision-envios")
public record PropiedadesVigilanciaRevision(
    int horasUmbral, int intervaloMinutos, int retrasoInicialMinutos, String destinatario) {

  public PropiedadesVigilanciaRevision {
    if (horasUmbral <= 0) {
      throw new IllegalStateException(
          "tecnosport.revision-envios.horas-umbral debe ser mayor que cero.");
    }
    if (intervaloMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.revision-envios.intervalo-minutos debe ser mayor que cero.");
    }
    if (retrasoInicialMinutos <= 0 || retrasoInicialMinutos >= intervaloMinutos) {
      throw new IllegalStateException(
          "tecnosport.revision-envios.retraso-inicial-minutos debe ser mayor que cero y"
              + " menor que el intervalo: si lo iguala, cada despliegue reinicia la cuenta y la"
              + " tarea puede no correr nunca.");
    }
    if (destinatario == null || destinatario.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.revision-envios.destinatario no puede estar vacío: un aviso sin"
              + " destinatario no avisa a nadie.");
    }
  }
}
