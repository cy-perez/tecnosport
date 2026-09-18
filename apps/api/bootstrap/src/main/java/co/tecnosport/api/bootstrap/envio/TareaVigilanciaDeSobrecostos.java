package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.AvisarSobrecostoDeEnvio;
import co.tecnosport.api.application.envio.ResultadoVigilanciaSobrecostos;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Le pregunta a la plataforma qué cobros extra aplicó la transportadora, y avisa de los nuevos.
 *
 * <p>Sin transacción, como las otras tareas de envío: lo único que escribe es la marca de "ya avisé
 * de este", una fila por cobro y cada una independiente de las demás. Agruparlas en una transacción
 * haría que un cobro ilegible tirara los avisos de los otros, y un correo enviado no se deshace con
 * un rollback de todas formas.
 *
 * <p><strong>Los tres desenlaces se registran distinto.</strong> "No hay cobros nuevos" no se
 * registra —sería una línea al día diciendo que todo está bien—; los cobros nuevos van en {@code
 * warn} porque es dinero que ya salió de la cuenta; y "no se pudo preguntar" también, aunque no sea
 * plata perdida: repetido durante días significa que esta vigilancia no está vigilando nada, y eso
 * no puede verse igual que una cuenta sin cobros.
 */
@Component
public class TareaVigilanciaDeSobrecostos {

  private static final Logger log = LoggerFactory.getLogger(TareaVigilanciaDeSobrecostos.class);

  private final AvisarSobrecostoDeEnvio avisarSobrecostoDeEnvio;

  public TareaVigilanciaDeSobrecostos(AvisarSobrecostoDeEnvio avisarSobrecostoDeEnvio) {
    this.avisarSobrecostoDeEnvio = Objects.requireNonNull(avisarSobrecostoDeEnvio);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.sobrecostos-envios.intervalo-minutos}",
      initialDelayString = "${tecnosport.sobrecostos-envios.retraso-inicial-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void vigilar() {
    ResultadoVigilanciaSobrecostos resultado = avisarSobrecostoDeEnvio.ejecutar();
    if (!resultado.seSupo()) {
      log.warn("No se pudieron consultar los cobros extra de la plataforma de envios.");
      return;
    }
    if (resultado.avisados() > 0) {
      log.warn(
          "La transportadora aplico {} cobro(s) extra nuevos, de {} en la ventana consultada: el"
              + " flete guardado de esos pedidos es menor que el que se pago.",
          resultado.avisados(),
          resultado.encontrados());
    }
  }
}
