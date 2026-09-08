package co.tecnosport.api.bootstrap.carrito;

import co.tecnosport.api.application.carrito.PurgarCarritosVencidos;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Segunda tarea programada del proyecto, con el mismo patrón que {@code TareaConciliacionWompi}:
 * transacción propia con {@code TransactionTemplate}, y un lote fallido se reintenta en la corrida
 * siguiente sin duplicar nada — borrar lo que ya no está es idempotente por naturaleza.
 *
 * <p>El intervalo por omisión es de horas, no de minutos: purgar carritos no es urgente. Un carrito
 * que sobrevive unas horas de más no le hace daño a nadie, y barrer cada minuto una tabla que casi
 * nunca tiene nada que borrar solo gasta base de datos.
 */
@Component
public class TareaPurgaCarritos {

  private static final Logger log = LoggerFactory.getLogger(TareaPurgaCarritos.class);

  private final PurgarCarritosVencidos purgarCarritosVencidos;
  private final TransactionTemplate transaccion;

  public TareaPurgaCarritos(
      PurgarCarritosVencidos purgarCarritosVencidos,
      PlatformTransactionManager transactionManager) {
    this.purgarCarritosVencidos = Objects.requireNonNull(purgarCarritosVencidos);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.carrito.purga.intervalo-minutos}",
      initialDelayString = "${tecnosport.carrito.purga.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void purgar() {
    int borrados = transaccion.execute(estado -> purgarCarritosVencidos.ejecutar());
    if (borrados > 0) {
      log.info("Purga de carritos: {} carritos inactivos borrados", borrados);
    }
  }
}
