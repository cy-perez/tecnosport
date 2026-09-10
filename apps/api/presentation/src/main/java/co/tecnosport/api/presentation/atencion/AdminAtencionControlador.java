package co.tecnosport.api.presentation.atencion;

import co.tecnosport.api.application.atencion.ListarSolicitudesDeAtencion;
import co.tecnosport.api.application.atencion.ProrrogarSolicitud;
import co.tecnosport.api.application.atencion.ProrrogarSolicitudComando;
import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.RadicarSolicitudComando;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitudComando;
import co.tecnosport.api.application.atencion.SolicitudConPlazo;
import co.tecnosport.api.domain.atencion.EstadoSolicitudAtencion;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.domain.atencion.TipoSolicitud;
import co.tecnosport.api.presentation.atencion.dto.ProrrogarSolicitudRequest;
import co.tecnosport.api.presentation.atencion.dto.RadicarSolicitudRequest;
import co.tecnosport.api.presentation.atencion.dto.ResponderSolicitudRequest;
import co.tecnosport.api.presentation.atencion.dto.SolicitudAtencionRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}.
 *
 * <p>No hay endpoint publico para radicar, y es deliberado: los terminos publicados prometen correo
 * y WhatsApp, no un formulario en el sitio. Quien radica es siempre una persona del negocio, igual
 * que en el retracto. El dia que se ofrezca un formulario habra que cambiar el texto primero.
 *
 * <p>Sin cabecera de idempotencia: la maquina de estados de la solicitud ya hace idempotentes las
 * acciones administrativas de un solo actor (docs/03-api.md).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAtencionControlador {

  private final RadicarSolicitud radicarSolicitud;
  private final ResponderSolicitud responderSolicitud;
  private final ProrrogarSolicitud prorrogarSolicitud;
  private final ListarSolicitudesDeAtencion listarSolicitudes;
  private final MapeadorRespuestasAtencion mapeador;
  private final TransactionTemplate transaccion;

  public AdminAtencionControlador(
      RadicarSolicitud radicarSolicitud,
      ResponderSolicitud responderSolicitud,
      ProrrogarSolicitud prorrogarSolicitud,
      ListarSolicitudesDeAtencion listarSolicitudes,
      MapeadorRespuestasAtencion mapeador,
      PlatformTransactionManager transactionManager) {
    this.radicarSolicitud = Objects.requireNonNull(radicarSolicitud);
    this.responderSolicitud = Objects.requireNonNull(responderSolicitud);
    this.prorrogarSolicitud = Objects.requireNonNull(prorrogarSolicitud);
    this.listarSolicitudes = Objects.requireNonNull(listarSolicitudes);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  /** Sin {@code estado} trae lo abierto, que es lo que la bandeja necesita mostrar. */
  @GetMapping("/atencion")
  public List<SolicitudAtencionRespuesta> listar(
      @RequestParam(required = false) EstadoSolicitudAtencion estado) {
    return listarSolicitudes.ejecutar(estado).stream().map(mapeador::aRespuesta).toList();
  }

  @GetMapping("/pedidos/{pedidoId}/atencion")
  public List<SolicitudAtencionRespuesta> listarDePedido(@PathVariable UUID pedidoId) {
    return listarSolicitudes.deUnPedido(pedidoId).stream().map(mapeador::aRespuesta).toList();
  }

  @PostMapping("/atencion")
  @ResponseStatus(HttpStatus.CREATED)
  public SolicitudAtencionRespuesta radicar(@RequestBody RadicarSolicitudRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudAtencion solicitud =
        transaccion.execute(
            estado ->
                radicarSolicitud.ejecutar(
                    new RadicarSolicitudComando(
                        TipoSolicitud.valueOf(cuerpo.tipo()),
                        cuerpo.correo(),
                        cuerpo.pedidoId(),
                        cuerpo.recibidaEn(),
                        cuerpo.asunto(),
                        actor)));
    return conPlazo(solicitud);
  }

  @PostMapping("/atencion/{id}/respuesta")
  public SolicitudAtencionRespuesta responder(
      @PathVariable UUID id, @RequestBody ResponderSolicitudRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudAtencion solicitud =
        transaccion.execute(
            estado ->
                responderSolicitud.ejecutar(
                    new ResponderSolicitudComando(id, cuerpo.resumen(), actor)));
    return conPlazo(solicitud);
  }

  @PostMapping("/atencion/{id}/prorroga")
  public SolicitudAtencionRespuesta prorrogar(
      @PathVariable UUID id, @RequestBody ProrrogarSolicitudRequest cuerpo) {
    String actor = "admin:" + actorId();
    SolicitudAtencion solicitud =
        transaccion.execute(
            estado ->
                prorrogarSolicitud.ejecutar(
                    new ProrrogarSolicitudComando(id, cuerpo.motivo(), actor)));
    return conPlazo(solicitud);
  }

  private SolicitudAtencionRespuesta conPlazo(SolicitudAtencion solicitud) {
    SolicitudConPlazo conPlazo = listarSolicitudes.conPlazo(solicitud);
    return mapeador.aRespuesta(conPlazo);
  }

  private UUID actorId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
