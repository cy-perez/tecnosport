package co.tecnosport.api.presentation.retracto;

import co.tecnosport.api.domain.retracto.Reembolso;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.presentation.retracto.dto.ReembolsoRespuesta;
import co.tecnosport.api.presentation.retracto.dto.SolicitudRetractoRespuesta;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasRetracto {

  public SolicitudRetractoRespuesta aRespuesta(SolicitudRetracto solicitud) {
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
        solicitud.reembolso().map(this::aRespuesta).orElse(null));
  }

  private ReembolsoRespuesta aRespuesta(Reembolso reembolso) {
    return new ReembolsoRespuesta(
        reembolso.monto().valor(),
        reembolso.medio().name(),
        reembolso.comprobanteOpcional().orElse(null),
        reembolso.registradoEn(),
        reembolso.registradoPor());
  }
}
