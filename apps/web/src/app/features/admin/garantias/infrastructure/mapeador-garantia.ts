import type { components } from '@tecnosport/contratos';
import {
  DesenlaceGarantia,
  EstadoReclamacionGarantia,
  ReclamacionGarantia,
  VigenciaGarantia,
} from '../domain/garantia.model';

type ReclamacionDto = components['schemas']['ReclamacionGarantiaRespuesta'];

/**
 * DTO generado -> modelo propio, mismo criterio que el resto del panel: los enums llegan como
 * `string` en el contrato porque springdoc no los expone como union literal, y el backend garantiza
 * que el valor es exactamente el nombre del enum.
 *
 * `mesesDeTermino` y `finDelTermino` se conservan nulos: nulo significa "no se sabe el termino",
 * que no es lo mismo que cero ni que una fecha por defecto.
 */
export function aReclamacionGarantia(dto: ReclamacionDto): ReclamacionGarantia {
  return {
    id: dto.id ?? '',
    solicitudId: dto.solicitudId ?? '',
    pedidoId: dto.pedidoId ?? '',
    varianteId: dto.varianteId ?? '',
    entregadoEn: dto.entregadoEn ?? '',
    radicadaEn: dto.radicadaEn ?? '',
    mesesDeTermino: dto.mesesDeTermino ?? null,
    finDelTermino: dto.finDelTermino ?? null,
    vigencia: (dto.vigencia ?? 'INDETERMINADA') as VigenciaGarantia,
    descripcionDelFallo: dto.descripcionDelFallo ?? '',
    estado: (dto.estado ?? 'RADICADA') as EstadoReclamacionGarantia,
    desenlace: (dto.desenlace ?? null) as DesenlaceGarantia | null,
    resueltaEn: dto.resueltaEn ?? null,
    resueltaPor: dto.resueltaPor ?? null,
    reintegroId: dto.reintegroId ?? null,
  };
}
