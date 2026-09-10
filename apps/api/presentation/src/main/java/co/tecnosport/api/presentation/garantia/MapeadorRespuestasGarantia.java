package co.tecnosport.api.presentation.garantia;

import co.tecnosport.api.domain.garantia.DesenlaceGarantia;
import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import co.tecnosport.api.presentation.garantia.dto.ReclamacionGarantiaRespuesta;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasGarantia {

  public ReclamacionGarantiaRespuesta aRespuesta(ReclamacionGarantia reclamacion) {
    return new ReclamacionGarantiaRespuesta(
        reclamacion.id().toString(),
        reclamacion.solicitudId().toString(),
        reclamacion.pedidoId().toString(),
        reclamacion.varianteId().toString(),
        reclamacion.entregadoEn(),
        reclamacion.radicadaEn(),
        reclamacion.mesesDeTermino().orElse(null),
        reclamacion.finDelTermino().orElse(null),
        reclamacion.vigencia().name(),
        reclamacion.descripcionDelFallo(),
        reclamacion.estado().name(),
        reclamacion.desenlace().map(DesenlaceGarantia::name).orElse(null),
        reclamacion.resueltaEn().orElse(null),
        reclamacion.resueltaPor().orElse(null),
        reclamacion.reintegroId().map(Object::toString).orElse(null));
  }
}
