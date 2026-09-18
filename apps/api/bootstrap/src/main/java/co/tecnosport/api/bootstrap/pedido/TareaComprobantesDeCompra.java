package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.pedido.EnviarComprobantesDeCompra;
import co.tecnosport.api.application.pedido.ResultadoComprobantes;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manda los comprobantes de compra de los pedidos que quedaron en firme desde la última vuelta.
 *
 * <p>Sin {@code TransactionTemplate}, por lo mismo que {@code TareaAvisoDePlazoDeEntrega}: la única
 * escritura del barrido es el reclamo de cada pedido, que se compromete solo. Envolver el lote
 * dejaría que un fallo al final revirtiera las marcas de los comprobantes ya enviados, y el
 * comprador recibiría el mismo correo en la vuelta siguiente.
 *
 * <p>El registro distingue los dos números porque significan cosas distintas: pendientes que no se
 * enviaron no es un fallo, es otra instancia habiéndose adelantado.
 */
@Component
public class TareaComprobantesDeCompra {

  private static final Logger log = LoggerFactory.getLogger(TareaComprobantesDeCompra.class);

  private final EnviarComprobantesDeCompra enviarComprobantesDeCompra;

  public TareaComprobantesDeCompra(EnviarComprobantesDeCompra enviarComprobantesDeCompra) {
    this.enviarComprobantesDeCompra = Objects.requireNonNull(enviarComprobantesDeCompra);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.pedido.comprobantes.intervalo-minutos}",
      initialDelayString = "${tecnosport.pedido.comprobantes.retraso-inicial-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void enviar() {
    ResultadoComprobantes resultado = enviarComprobantesDeCompra.ejecutar();
    if (resultado.fallidos() > 0) {
      log.warn(
          "Comprobantes de compra: {} pedidos en firme sin comprobante, {} enviados, {} fallaron y"
              + " se reintentarán en la vuelta siguiente.",
          resultado.pendientes(),
          resultado.enviados(),
          resultado.fallidos());
    } else if (resultado.pendientes() > 0) {
      log.info(
          "Comprobantes de compra: {} pedidos en firme sin comprobante, {} enviados por esta"
              + " instancia.",
          resultado.pendientes(),
          resultado.enviados());
    }
  }
}
