package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.pago.ConciliarPagosSistecredito;
import co.tecnosport.api.application.pago.ResultadoConciliacion;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gemela de {@code TareaConciliacionWompi}: una transacción por pago, con la consulta a la pasarela
 * fuera de ella. Lo que falle se reintenta en la corrida siguiente — el mecanismo es idempotente
 * por diseño.
 *
 * <p>Corre aunque el método esté apagado, y no pasa nada: sin pagos de Sistecrédito la consulta
 * devuelve una lista vacía y la tarea no llama a nadie.
 */
@Component
public class TareaConciliacionSistecredito {

  private static final Logger log = LoggerFactory.getLogger(TareaConciliacionSistecredito.class);

  private final ConciliarPagosSistecredito conciliarPagos;

  public TareaConciliacionSistecredito(ConciliarPagosSistecredito conciliarPagos) {
    this.conciliarPagos = Objects.requireNonNull(conciliarPagos);
  }

  public void conciliar() {
    ResultadoConciliacion resultado = conciliarPagos.ejecutar();
    if (resultado.revisados() == 0) {
      return;
    }
    log.info(
        "Conciliación Sistecrédito: {} revisados, {} conciliados, {} sin novedad",
        resultado.revisados(),
        resultado.conciliados(),
        resultado.sinNovedad());
    if (!resultado.errores().isEmpty()) {
      log.error(
          "Conciliación: {} pagos fallaron y se reintentan en la vuelta siguiente: {}",
          resultado.errores().size(),
          resultado.errores());
    }
  }
}
