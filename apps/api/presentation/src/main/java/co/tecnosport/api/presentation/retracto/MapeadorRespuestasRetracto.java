package co.tecnosport.api.presentation.retracto;

import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.presentation.retracto.dto.ReintegroRespuesta;
import co.tecnosport.api.presentation.retracto.dto.SolicitudRetractoRespuesta;
import org.springframework.stereotype.Component;

/**
 * El reintegro llega por fuera de la solicitud porque son dos agregados: la solicitud guarda su id,
 * no la constancia entera. Quien llame lo resuelve y lo pasa; {@code null} cuando todavia no hay.
 */
@Component
public class MapeadorRespuestasRetracto {

  public SolicitudRetractoRespuesta aRespuesta(SolicitudRetracto solicitud, Reintegro reintegro) {
    return new SolicitudRetractoRespuesta(
        solicitud.id().toString(),
        solicitud.pedidoId().toString(),
        solicitud.radicadaEn(),
        solicitud.radicadaPor(),
        solicitud.motivo().orElse(null),
        solicitud.verdictoAlRadicar().name(),
        solicitud.estado().name(),
        solicitud.productoRecibidoEn().orElse(null),
        solicitud.limiteDeReintegro().orElse(null),
        reintegro == null ? null : aRespuesta(reintegro));
  }

  private ReintegroRespuesta aRespuesta(Reintegro reintegro) {
    return new ReintegroRespuesta(
        reintegro.id().toString(),
        reintegro.motivo().name(),
        reintegro.monto().valor(),
        reintegro.medio().name(),
        reintegro.comprobante().orElse(null),
        reintegro.registradoEn(),
        reintegro.registradoPor());
  }
}
