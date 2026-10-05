package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.pedido.VencerContraentregaSinVerificar;
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
 * Cancela los pedidos contraentrega que nadie verificó a tiempo. Una transacción por pedido: uno
 * que falle no se lleva por delante a los demás, la lección de la conciliación de envíos.
 */
@Component
public class TareaVencerContraentregas {

  private static final Logger log = LoggerFactory.getLogger(TareaVencerContraentregas.class);

  private final VencerContraentregaSinVerificar vencer;
  private final TransactionTemplate transaccion;

  public TareaVencerContraentregas(
      VencerContraentregaSinVerificar vencer, PlatformTransactionManager transactionManager) {
    this.vencer = Objects.requireNonNull(vencer);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.contraentrega.vencimiento.intervalo-minutos}",
      initialDelayString = "${tecnosport.contraentrega.vencimiento.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void vencer() {
    int cancelados = 0;
    for (UUID pedidoId : vencer.vencidos()) {
      try {
        Boolean cancelado = transaccion.execute(estado -> vencer.ejecutar(pedidoId));
        if (Boolean.TRUE.equals(cancelado)) {
          cancelados++;
        }
      } catch (RuntimeException e) {
        log.error("No se pudo vencer el pedido contraentrega {}; se reintenta luego", pedidoId, e);
      }
    }
    if (cancelados > 0) {
      log.info("Contraentregas sin verificar canceladas: {}", cancelados);
    }
  }
}
