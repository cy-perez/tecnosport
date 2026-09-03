package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.CrearIntentoDePagoComando;
import co.tecnosport.api.application.pago.IntentoDePago;
import co.tecnosport.api.application.pago.ProcesarEventoDePago;
import co.tecnosport.api.application.pago.ProcesarEventoDePagoComando;
import co.tecnosport.api.application.pago.RegistrarIdTransaccionWompi;
import co.tecnosport.api.application.pago.RegistrarIdTransaccionWompiComando;
import co.tecnosport.api.application.pago.ResultadoEventoDePago;
import co.tecnosport.api.presentation.pago.dto.CrearIntentoDePagoRequest;
import co.tecnosport.api.presentation.pago.dto.IntentoDePagoRespuesta;
import co.tecnosport.api.presentation.pago.dto.RegistrarIdTransaccionWompiRequest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * {@code /intentos}, {@code /intentos/{referencia}} y {@code /webhook} comparten controlador porque
 * comparten dependencias (transacción, casos de uso de {@code Pago}), no por una razón de diseño
 * más profunda.
 *
 * <p>{@code webhook} siempre responde 200, incluso cuando el evento se descarta
 * (docs/11-pagos-y-envios.md: "se descarta y se registra") — un código distinto haría que Wompi
 * reintentara algo que un reintento no puede arreglar (firma inválida, referencia inexistente).
 */
@RestController
@RequestMapping("/api/v1/pagos")
public class PagoControlador {

  private static final Logger log = LoggerFactory.getLogger(PagoControlador.class);

  private final CrearIntentoDePago crearIntentoDePago;
  private final ProcesarEventoDePago procesarEventoDePago;
  private final RegistrarIdTransaccionWompi registrarIdTransaccionWompi;
  private final MapeadorRespuestasPago mapeador;
  private final TransactionTemplate transaccion;

  public PagoControlador(
      CrearIntentoDePago crearIntentoDePago,
      ProcesarEventoDePago procesarEventoDePago,
      RegistrarIdTransaccionWompi registrarIdTransaccionWompi,
      MapeadorRespuestasPago mapeador,
      PlatformTransactionManager transactionManager) {
    this.crearIntentoDePago = Objects.requireNonNull(crearIntentoDePago);
    this.procesarEventoDePago = Objects.requireNonNull(procesarEventoDePago);
    this.registrarIdTransaccionWompi = Objects.requireNonNull(registrarIdTransaccionWompi);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping("/intentos")
  public IntentoDePagoRespuesta crear(@RequestBody CrearIntentoDePagoRequest cuerpo) {
    CrearIntentoDePagoComando comando = new CrearIntentoDePagoComando(cuerpo.pedidoId());
    IntentoDePago intento = transaccion.execute(estado -> crearIntentoDePago.ejecutar(comando));
    return mapeador.aRespuesta(intento);
  }

  /**
   * El id de transacción de Wompi llega en la URL de retorno del Web Checkout
   * (docs/11-pagos-y-envios.md) — el frontend lo reporta aquí al volver. Sin él, la conciliación
   * programada no tiene cómo consultar este pago en la API de Wompi.
   */
  @PatchMapping("/intentos/{referencia}")
  public void registrarIdTransaccion(
      @PathVariable String referencia, @RequestBody RegistrarIdTransaccionWompiRequest cuerpo) {
    RegistrarIdTransaccionWompiComando comando =
        new RegistrarIdTransaccionWompiComando(referencia, cuerpo.idTransaccionWompi());
    transaccion.executeWithoutResult(estado -> registrarIdTransaccionWompi.ejecutar(comando));
  }

  @PostMapping("/webhook")
  public ResponseEntity<Void> webhook(@RequestBody JsonNode cuerpo) {
    if (!LectorEventoWompi.esActualizacionDeTransaccion(cuerpo)) {
      return ResponseEntity.ok().build();
    }

    ProcesarEventoDePagoComando comando =
        new ProcesarEventoDePagoComando(
            LectorEventoWompi.referencia(cuerpo),
            LectorEventoWompi.estado(cuerpo),
            LectorEventoWompi.valoresDePropiedadesFirmadas(cuerpo),
            LectorEventoWompi.timestamp(cuerpo),
            LectorEventoWompi.checksum(cuerpo));
    ResultadoEventoDePago resultado =
        transaccion.execute(estado -> procesarEventoDePago.ejecutar(comando));
    registrar(resultado, comando);
    return ResponseEntity.ok().build();
  }

  private void registrar(ResultadoEventoDePago resultado, ProcesarEventoDePagoComando comando) {
    switch (resultado) {
      case FIRMA_INVALIDA ->
          log.warn("Firma de webhook de Wompi inválida, referencia={}", comando.referencia());
      case PAGO_NO_ENCONTRADO ->
          log.warn(
              "Webhook de Wompi para una referencia sin pago propio: {}", comando.referencia());
      case ESTADO_NO_SOPORTADO ->
          log.info(
              "Estado de Wompi no soportado todavía: {} (referencia={})",
              comando.estadoWompi(),
              comando.referencia());
      case YA_PROCESADO ->
          log.debug("Evento de Wompi repetido, referencia={}", comando.referencia());
      case APLICADO ->
          log.info(
              "Evento de Wompi aplicado, referencia={}, estado={}",
              comando.referencia(),
              comando.estadoWompi());
    }
  }
}
