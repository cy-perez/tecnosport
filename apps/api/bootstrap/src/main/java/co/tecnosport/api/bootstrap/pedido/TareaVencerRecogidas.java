package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.pedido.VencerRecogidasSinRetirar;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancela las recogidas con pago en el punto que nadie vino a recoger a tiempo (ADR-0094). Una
 * transacción por pedido, como {@link TareaVencerContraentregas}: uno que falle no se lleva por
 * delante a los demás.
 */
@Component
public class TareaVencerRecogidas {

  private static final Logger log = LoggerFactory.getLogger(TareaVencerRecogidas.class);

  private final VencerRecogidasSinRetirar vencer;
  private final TransactionTemplate transaccion;

  public TareaVencerRecogidas(
      VencerRecogidasSinRetirar vencer, PlatformTransactionManager transactionManager) {
    this.vencer = Objects.requireNonNull(vencer);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.retiro-en-punto.vencimiento-intervalo-minutos}",
      initialDelayString = "${tecnosport.retiro-en-punto.vencimiento-intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void vencer() {
    int canceladas = 0;
    for (UUID pedidoId : vencer.vencidas()) {
      try {
        Boolean cancelada = transaccion.execute(estado -> vencer.ejecutar(pedidoId));
        if (Boolean.TRUE.equals(cancelada)) {
          canceladas++;
        }
      } catch (RuntimeException e) {
        log.error("No se pudo vencer la recogida {}; se reintenta luego", pedidoId, e);
      }
    }
    if (canceladas > 0) {
      log.info("Recogidas sin retirar canceladas: {}", canceladas);
    }
  }
}
