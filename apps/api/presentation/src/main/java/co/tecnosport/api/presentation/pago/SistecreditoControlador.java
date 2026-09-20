package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.CrearIntentoDePagoSistecredito;
import co.tecnosport.api.application.pago.CrearIntentoDePagoSistecreditoComando;
import co.tecnosport.api.application.pago.IntentoDePagoSistecredito;
import co.tecnosport.api.application.pago.ProcesarNotificacionSistecredito;
import co.tecnosport.api.application.pago.ProcesarNotificacionSistecreditoComando;
import co.tecnosport.api.application.pago.ResultadoNotificacionSistecredito;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.DocumentoIdentidad;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pago.dto.CrearIntentoSistecreditoRequest;
import co.tecnosport.api.presentation.pago.dto.IntentoSistecreditoRespuesta;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Los dos extremos de Sistecrédito ({@code adr/0048}): el intento que abre el pago y la
 * notificación que lo cierra. Controlador aparte de {@code PagoControlador} —y no dos métodos más
 * ahí— porque no comparten ni casos de uso ni forma: allá el servidor firma y el navegador arma la
 * URL; aquí el servidor la pide y la entrega hecha.
 *
 * <p>{@code /confirmacion} siempre responde 200, por el mismo motivo que el webhook de Wompi: un
 * código distinto haría que la pasarela reintentara algo que un reintento no arregla.
 */
@RestController
@RequestMapping("/api/v1/pagos/sistecredito")
public class SistecreditoControlador {

  private static final Logger log = LoggerFactory.getLogger(SistecreditoControlador.class);

  private final CrearIntentoDePagoSistecredito crearIntento;
  private final ProcesarNotificacionSistecredito procesarNotificacion;
  private final TransactionTemplate transaccion;

  public SistecreditoControlador(
      CrearIntentoDePagoSistecredito crearIntento,
      ProcesarNotificacionSistecredito procesarNotificacion,
      PlatformTransactionManager transactionManager) {
    this.crearIntento = Objects.requireNonNull(crearIntento);
    this.procesarNotificacion = Objects.requireNonNull(procesarNotificacion);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping("/intentos")
  public IntentoSistecreditoRespuesta crear(@RequestBody CrearIntentoSistecreditoRequest cuerpo) {
    CrearIntentoDePagoSistecreditoComando comando =
        new CrearIntentoDePagoSistecreditoComando(
            cuerpo.pedidoId(),
            new DocumentoIdentidad(cuerpo.tipoDocumento(), cuerpo.documento()),
            cuerpo.idioma());
    IntentoDePagoSistecredito intento =
        transaccion.execute(estado -> crearIntento.ejecutar(comando));
    return new IntentoSistecreditoRespuesta(
        intento.referencia().valor(),
        new DineroRespuesta(intento.monto().valor().longValueExact(), Dinero.MONEDA),
        intento.urlRedireccion());
  }

  /**
   * El endpoint que Sistecrédito llama en cada cambio de estado. Es público —la pasarela tiene que
   * poder alcanzarlo— y <b>no viene firmado</b>: lo que lo autentica es el contraste contra la
   * consulta que hace el caso de uso, no nada de lo que llegue aquí. Por eso este método no valida
   * el cuerpo más allá de sacarle tres campos.
   */
  @PostMapping("/confirmacion")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description =
          "Notificación de Sistecrédito, tal como la envía la pasarela. No viene firmada: se"
              + " verifica consultando la transacción.",
      content =
          @Content(
              schema =
                  @Schema(
                      type = "object",
                      additionalProperties = Schema.AdditionalPropertiesValue.TRUE)))
  public ResponseEntity<Void> confirmacion(@RequestBody JsonNode cuerpo) {
    ProcesarNotificacionSistecreditoComando comando =
        new ProcesarNotificacionSistecreditoComando(
            LectorNotificacionSistecredito.idTransaccion(cuerpo),
            LectorNotificacionSistecredito.referencia(cuerpo),
            LectorNotificacionSistecredito.estado(cuerpo));
    ResultadoNotificacionSistecredito resultado =
        transaccion.execute(estado -> procesarNotificacion.ejecutar(comando));
    registrar(resultado, comando);
    return ResponseEntity.ok().build();
  }

  private void registrar(
      ResultadoNotificacionSistecredito resultado,
      ProcesarNotificacionSistecreditoComando comando) {
    switch (resultado) {
      case NO_SE_PUDO_VERIFICAR ->
          log.warn(
              "Notificación de Sistecrédito que no se pudo verificar contra la pasarela; no se"
                  + " aplicó nada, queda para la conciliación. transaccion={}, referencia={}",
              comando.idTransaccion(),
              comando.referencia());
      case DISCREPANCIA_CON_LA_PASARELA ->
          log.error(
              "Notificación de Sistecrédito que NO coincide con lo que responde la pasarela. O"
                  + " alguien intentó falsificarla, o la transacción cambió en medio. No se aplicó"
                  + " nada. transaccion={}, referencia={}, estadoNotificado={}",
              comando.idTransaccion(),
              comando.referencia(),
              comando.estado());
      case PAGO_NO_ENCONTRADO ->
          log.warn(
              "Notificación de Sistecrédito para una referencia sin pago propio: {}",
              comando.referencia());
      case ESTADO_NO_SOPORTADO ->
          log.info(
              "Estado de Sistecrédito que todavía no es un resultado: {} (referencia={})",
              comando.estado(),
              comando.referencia());
      case YA_PROCESADO ->
          log.debug("Notificación de Sistecrédito repetida, referencia={}", comando.referencia());
      case APLICADO ->
          log.info(
              "Notificación de Sistecrédito aplicada, referencia={}, estado={}",
              comando.referencia(),
              comando.estado());
      case APLICADO_SIN_CONFIRMAR_INVENTARIO ->
          log.error(
              "Pago de Sistecrédito aplicado pero no se pudo confirmar/liberar la reserva de"
                  + " inventario (venció o ya se había resuelto) — riesgo de sobreventa, revisar a"
                  + " mano. referencia={}, estado={}",
              comando.referencia(),
              comando.estado());
    }
  }
}
