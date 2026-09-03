package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.CrearIntentoDePagoComando;
import co.tecnosport.api.application.pago.IntentoDePago;
import co.tecnosport.api.presentation.pago.dto.CrearIntentoDePagoRequest;
import co.tecnosport.api.presentation.pago.dto.IntentoDePagoRespuesta;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code CrearIntentoDePago.ejecutar} guarda el {@code Pago} y sus eventos en varias sentencias
 * (ver {@code RepositorioPagosJpa}); igual que {@code PedidoControlador}, este controlador abre la
 * transacción que las mantiene juntas.
 */
@RestController
@RequestMapping("/api/v1/pagos")
public class PagoControlador {

  private final CrearIntentoDePago crearIntentoDePago;
  private final MapeadorRespuestasPago mapeador;
  private final TransactionTemplate transaccion;

  public PagoControlador(
      CrearIntentoDePago crearIntentoDePago,
      MapeadorRespuestasPago mapeador,
      PlatformTransactionManager transactionManager) {
    this.crearIntentoDePago = Objects.requireNonNull(crearIntentoDePago);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping("/intentos")
  public IntentoDePagoRespuesta crear(@RequestBody CrearIntentoDePagoRequest cuerpo) {
    CrearIntentoDePagoComando comando = new CrearIntentoDePagoComando(cuerpo.pedidoId());
    IntentoDePago intento = transaccion.execute(estado -> crearIntentoDePago.ejecutar(comando));
    return mapeador.aRespuesta(intento);
  }
}
