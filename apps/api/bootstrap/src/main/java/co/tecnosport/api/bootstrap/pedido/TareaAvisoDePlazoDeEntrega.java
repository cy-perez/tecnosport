package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.pedido.AvisarPlazosDeEntregaVencidos;
import co.tecnosport.api.application.pedido.ResultadoVigilanciaPlazos;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La tercera tarea programada del sistema, junto a la purga de carritos y la conciliación de Wompi.
 *
 * <p><b>Sin {@code TransactionTemplate}, a diferencia de las otras dos</b>, y no es un olvido: la
 * única escritura del barrido es el reclamo atómico de cada pedido, que se compromete solo (ver
 * {@code RepositorioPedidos.reclamarAvisoDePlazo}). Envolver el lote entero era justo el defecto:
 * un fallo al comprometer revertía las marcas de todos los pedidos a los que ya se les había
 * escrito, y el vigilante les mandaba el mismo correo otra vez doce horas después.
 *
 * <p>El retraso inicial es corto y el intervalo largo, y esa asimetría también tiene motivo: con un
 * retraso inicial igual al intervalo, cada despliegue reinicia la cuenta, y desplegando más de una
 * vez al día el vigilante no correría <b>nunca</b>. Es un plazo de treinta días: lo que importa es
 * que corra, no cuándo.
 */
@Component
public class TareaAvisoDePlazoDeEntrega {

  private static final Logger log = LoggerFactory.getLogger(TareaAvisoDePlazoDeEntrega.class);

  private final AvisarPlazosDeEntregaVencidos avisarPlazosDeEntregaVencidos;

  public TareaAvisoDePlazoDeEntrega(AvisarPlazosDeEntregaVencidos avisarPlazosDeEntregaVencidos) {
    this.avisarPlazosDeEntregaVencidos = Objects.requireNonNull(avisarPlazosDeEntregaVencidos);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.pedido.vigilancia-plazo.intervalo-minutos}",
      initialDelayString = "${tecnosport.pedido.vigilancia-plazo.retraso-inicial-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void vigilar() {
    ResultadoVigilanciaPlazos resultado = avisarPlazosDeEntregaVencidos.ejecutar();
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
