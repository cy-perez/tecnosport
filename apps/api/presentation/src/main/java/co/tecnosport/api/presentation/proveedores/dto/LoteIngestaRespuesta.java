package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Instant;
import java.util.UUID;

/** Un lote con su resumen, que va vacío mientras el lote no termine. */
public record LoteIngestaRespuesta(
    UUID id,
    UUID proveedorId,
    String origen,
    String estado,
    ResumenIngestaRespuesta resumen,
    String detalleError,
    Instant creadoEn,
    Instant iniciadoEn,
    Instant terminadoEn) {

  public static LoteIngestaRespuesta de(LoteIngesta lote) {
    return new LoteIngestaRespuesta(
        lote.id(),
        lote.proveedorId(),
        lote.origen().name(),
        lote.estado().name(),
        lote.resumen().map(ResumenIngestaRespuesta::de).orElse(null),
        lote.detalleError().orElse(null),
        lote.creadoEn(),
        lote.iniciadoEn().orElse(null),
        lote.terminadoEn().orElse(null));
  }
}
