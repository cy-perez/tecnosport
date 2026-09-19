package co.tecnosport.api.bootstrap.correo;

import co.tecnosport.api.application.compartido.DrenarBandejaDeSalida;
import co.tecnosport.api.application.compartido.ResultadoDrenaje;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manda los correos que están esperando en la bandeja de salida.
 *
 * <p>Sin {@code TransactionTemplate}, por lo mismo que {@code TareaComprobantesDeCompra}: cada
 * correo se compromete por su cuenta, y envolver el lote dejaría que un fallo al final revirtiera
 * las marcas de los que ya salieron — que es como alguien recibe el mismo mensaje dos veces.
 *
 * <p><b>Este registro es la única señal que hay, y por una razón incómoda:</b> el aviso de que los
 * correos no salen no puede ser un correo. Los otros vigilantes del sistema avisan por correo
 * porque su fallo no afecta al correo; el de la bandeja sí, así que aquí no queda más que escribir
 * la línea y que la recoja una alerta de Cloud Logging. Montar esa alerta es infraestructura y
 * pertenece a la etapa de producción, no a este componente.
 *
 * <p>Por eso los tres desenlaces se registran distinto: una vuelta que envía todo no se registra
 * —sería una línea cada minuto diciendo que todo está bien—; los fallidos van en {@code warn}
 * porque se van a reintentar; y los <b>rendidos</b> van en {@code error}, que es el único que lo
 * merece: ese correo ya no se vuelve a intentar, y si era un comprobante de compra hay alguien sin
 * el documento de su venta.
 *
 * <p>Solo contadores, nunca el destinatario ni el cuerpo ni el texto del fallo de SMTP, que suele
 * repetir la dirección: docs/08-seguridad-legal.md. El detalle vive en la columna {@code
 * ultimo_error} de la fila, que es donde se puede mirar sin publicarlo en un registro.
 */
@Component
public class TareaBandejaDeSalida {

  private static final Logger log = LoggerFactory.getLogger(TareaBandejaDeSalida.class);

  private final DrenarBandejaDeSalida drenarBandejaDeSalida;

  public TareaBandejaDeSalida(DrenarBandejaDeSalida drenarBandejaDeSalida) {
    this.drenarBandejaDeSalida = Objects.requireNonNull(drenarBandejaDeSalida);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.correo.bandeja.intervalo-minutos}",
      initialDelayString = "${tecnosport.correo.bandeja.retraso-inicial-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void drenar() {
    ResultadoDrenaje resultado = drenarBandejaDeSalida.ejecutar();
    if (resultado.rendidos() > 0) {
      log.error(
          "Bandeja de salida: {} correos se rindieron tras {} intentos y no se volverán a"
              + " intentar. El motivo de cada uno está en correo_pendiente.ultimo_error.",
          resultado.rendidos(),
          DrenarBandejaDeSalida.MAX_INTENTOS);
    }
    if (resultado.fallidos() > resultado.rendidos()) {
      log.warn(
          "Bandeja de salida: {} pendientes, {} enviados, {} fallaron y se reintentarán.",
          resultado.pendientes(),
          resultado.enviados(),
          resultado.fallidos() - resultado.rendidos());
    }
    if (resultado.purgados() > 0) {
      log.info(
          "Bandeja de salida: {} correos enviados se purgaron por antigüedad.",
          resultado.purgados());
    }
  }
}
