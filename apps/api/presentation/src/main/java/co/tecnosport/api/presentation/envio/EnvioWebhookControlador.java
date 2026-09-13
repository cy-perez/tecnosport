package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.RecibirEventoDeEnvio;
import co.tecnosport.api.application.envio.ResultadoEventoDeEnvio;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Los eventos de seguimiento de Skydropx. Público, con firma verificada antes de aplicar nada, y
 * siempre 200 — incluso cuando descarta (adr/0022, docs/03-api.md).
 *
 * <p><strong>El cuerpo se recibe como cadena y no como JSON parseado.</strong> Es deliberado: la
 * firma es un HMAC sobre los bytes que llegaron, y volver a serializar un objeto reordena claves y
 * cambia espacios, con lo que la firma deja de cuadrar por un motivo que nadie encuentra mirando el
 * código. El lector se encarga de parsearlo después, cuando ya está verificado.
 *
 * <p><strong>Siempre 200, y no por pereza.</strong> Firma inválida y guía desconocida no se
 * arreglan reintentando, así que un 4xx solo metería este endpoint en el ciclo de reintentos de la
 * plataforma y llenaría los registros de ruido. Lo que sí se hace con cada descarte es dejarlo
 * escrito.
 */
@RestController
@RequestMapping("/api/v1/envios/webhook")
public class EnvioWebhookControlador {

  private static final Logger log = LoggerFactory.getLogger(EnvioWebhookControlador.class);

  private final RecibirEventoDeEnvio recibirEvento;
  private final TransactionTemplate transaccion;
  private final String cabeceraFirma;

  public EnvioWebhookControlador(
      RecibirEventoDeEnvio recibirEvento,
      PlatformTransactionManager transactionManager,
      PropiedadesWebhookEnvio propiedades) {
    this.recibirEvento = Objects.requireNonNull(recibirEvento);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.cabeceraFirma = Objects.requireNonNull(propiedades).cabeceraFirma();
  }

  /**
   * La cabecera se lee de la petición y no con {@code @RequestHeader} porque su nombre es
   * configuración, y una anotación exige una constante de compilación. {@code getHeader} es
   * insensible a mayúsculas por especificación de servlets, así que da igual cómo la escriba el
   * proveedor.
   *
   * <p>Una petición sin firma no se rechaza aquí: tiene que llegar al verificador y que él la
   * descarte. Morir antes con un 400 metería al proveedor en su ciclo de reintentos.
   */
  @PostMapping
  public ResponseEntity<Void> webhook(@RequestBody String cuerpo, HttpServletRequest peticion) {
    String firma = peticion.getHeader(cabeceraFirma);
    ResultadoEventoDeEnvio resultado =
        transaccion.execute(estado -> recibirEvento.ejecutar(cuerpo, firma));
    registrar(resultado);
    return ResponseEntity.ok().build();
  }

  private void registrar(ResultadoEventoDeEnvio resultado) {
    switch (resultado) {
      case FIRMA_INVALIDA ->
          log.warn(
              "Evento de Skydropx descartado por firma: no verificable todavía"
                  + " (docs/13-skydropx-capacidades.md, sección 6)");
      case NO_SE_PUDO_LEER ->
          log.warn("Evento de Skydropx con firma válida y cuerpo que no se supo leer");
      case GUIA_DESCONOCIDA -> log.warn("Evento de Skydropx para una guía que no es nuestra");
      case REPETIDO -> log.info("Evento de Skydropx repetido; ya estaba registrado");
      case REGISTRADO -> log.info("Evento de Skydropx registrado, sin efecto sobre el pedido");
      case REGISTRADO_Y_APLICADO -> log.info("Evento de Skydropx registrado y aplicado al pedido");
    }
  }
}
