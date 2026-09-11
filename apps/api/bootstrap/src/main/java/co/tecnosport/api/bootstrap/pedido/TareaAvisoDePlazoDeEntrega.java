package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.pedido.AvisarPlazosDeEntregaVencidos;
import co.tecnosport.api.application.pedido.ResultadoVigilanciaPlazos;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La tercera tarea programada del sistema, junto a la purga de carritos y la conciliación de Wompi.
 *
 * <p>Corre {@code AvisarPlazosDeEntregaVencidos} dentro de su propia transacción, igual que {@code
 * TareaConciliacionWompi}: sin eso, un fallo a mitad del lote podría dejar pedidos marcados como
 * avisados sin que la marca se comprometa. Un lote fallido se reintenta en la siguiente corrida —
 * la marca por pedido es lo que hace el mecanismo idempotente.
 */
@Component
public class TareaAvisoDePlazoDeEntrega {

  private static final Logger log = LoggerFactory.getLogger(TareaAvisoDePlazoDeEntrega.class);

  private final AvisarPlazosDeEntregaVencidos avisarPlazosDeEntregaVencidos;
  private final TransactionTemplate transaccion;

  public TareaAvisoDePlazoDeEntrega(
      AvisarPlazosDeEntregaVencidos avisarPlazosDeEntregaVencidos,
      PlatformTransactionManager transactionManager) {
    this.avisarPlazosDeEntregaVencidos = Objects.requireNonNull(avisarPlazosDeEntregaVencidos);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.pedido.vigilancia-plazo.intervalo-horas}",
      initialDelayString = "${tecnosport.pedido.vigilancia-plazo.intervalo-horas}",
      timeUnit = TimeUnit.HOURS)
  public void vigilar() {
    ResultadoVigilanciaPlazos resultado =
        transaccion.execute(estado -> avisarPlazosDeEntregaVencidos.ejecutar());
    if (resultado.avisados() > 0) {
      log.warn(
          "Plazo de entrega vencido: {} pedidos revisados, {} avisados. Ninguno se canceló:"
              + " terminar el contrato lo decide quien compró (ADR-0028).",
          resultado.revisados(),
          resultado.avisados());
    } else {
      log.info("Plazo de entrega: {} pedidos revisados, ninguno vencido.", resultado.revisados());
    }
  }
}
