package co.tecnosport.api.presentation.atencion;

import co.tecnosport.api.application.atencion.SolicitudConPlazo;
import co.tecnosport.api.domain.atencion.Prorroga;
import co.tecnosport.api.domain.atencion.Respuesta;
import co.tecnosport.api.domain.atencion.SolicitudAtencion;
import co.tecnosport.api.presentation.atencion.dto.ProrrogaRespuesta;
import co.tecnosport.api.presentation.atencion.dto.RespuestaRespuesta;
import co.tecnosport.api.presentation.atencion.dto.SolicitudAtencionRespuesta;
import org.springframework.stereotype.Component;

/**
 * Recibe la solicitud con su plazo ya resuelto ({@code SolicitudConPlazo}) y no la calcula aqui: el
 * limite depende del calendario y de los plazos configurados, que son decisiones de aplicacion, no
 * de presentacion.
 */
@Component
public class MapeadorRespuestasAtencion {

  public SolicitudAtencionRespuesta aRespuesta(SolicitudConPlazo conPlazo) {
    SolicitudAtencion solicitud = conPlazo.solicitud();
    return new SolicitudAtencionRespuesta(
        solicitud.id().toString(),
        solicitud.numeroRadicado().valor(),
        solicitud.tipo().name(),
        solicitud.correo().valor(),
        solicitud.pedidoId().map(Object::toString).orElse(null),
        solicitud.recibidaEn(),
        solicitud.radicadaEn(),
        solicitud.radicadaPor(),
        solicitud.asunto(),
        solicitud.estado().name(),
        conPlazo.limiteDeRespuesta(),
        conPlazo.verdicto().name(),
        solicitud.prorroga().map(this::aRespuesta).orElse(null),
        solicitud.respuesta().map(this::aRespuesta).orElse(null));
  }

  private ProrrogaRespuesta aRespuesta(Prorroga prorroga) {
    return new ProrrogaRespuesta(
        prorroga.otorgadaEn(), prorroga.otorgadaPor(), prorroga.motivo(), prorroga.avisadaEn());
  }

  private RespuestaRespuesta aRespuesta(Respuesta respuesta) {
    return new RespuestaRespuesta(
        respuesta.respondidaEn(), respuesta.respondidaPor(), respuesta.resumen());
  }
}
