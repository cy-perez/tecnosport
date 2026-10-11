package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.FalloDeLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Instant;
import java.util.UUID;

/**
 * Un lote con su resumen, que va vacío mientras el lote no termine.
 *
 * <p>Si falló, {@code codigoError} dice por qué y {@code datoError} lleva el valor que el texto
 * interpola, si hay uno. El panel traduce el código (regla dura 4): el servidor no manda frases.
 * Hasta el 11 de octubre de 2026 iba {@code detalleError}, la frase en español.
 */
public record LoteIngestaRespuesta(
    UUID id,
    UUID proveedorId,
    String origen,
    String estado,
    ResumenIngestaRespuesta resumen,
    String codigoError,
    String datoError,
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
        lote.fallo().map(f -> f.motivo().name()).orElse(null),
        lote.fallo().map(FalloDeLote::dato).orElse(null),
        lote.creadoEn(),
        lote.iniciadoEn().orElse(null),
        lote.terminadoEn().orElse(null));
  }
}
