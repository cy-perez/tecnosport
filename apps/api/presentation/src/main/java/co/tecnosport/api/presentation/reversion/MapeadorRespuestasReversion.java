package co.tecnosport.api.presentation.reversion;

import co.tecnosport.api.domain.reversion.DesenlaceReversion;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import co.tecnosport.api.presentation.reversion.dto.SolicitudReversionRespuesta;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasReversion {

  public SolicitudReversionRespuesta aRespuesta(SolicitudReversion solicitud) {
    return new SolicitudReversionRespuesta(
        solicitud.id().toString(),
        solicitud.solicitudId().toString(),
        solicitud.pedidoId().toString(),
        solicitud.causal().name(),
        solicitud.fechaDelHecho(),
        solicitud.radicadaEn(),
        solicitud.verdictoAlRadicar().name(),
        solicitud.estado().name(),
        solicitud.gestionadaEn().orElse(null),
        solicitud.gestionadaPor().orElse(null),
        solicitud.gestion().orElse(null),
        solicitud.desenlace().map(DesenlaceReversion::name).orElse(null),
        solicitud.resueltaEn().orElse(null),
        solicitud.reintegroId().map(Object::toString).orElse(null));
  }
}
