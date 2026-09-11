import type { components } from '@tecnosport/contratos';
import { VerdictoPlazo } from '../../retractos/domain/retracto.model';
import {
  CausalReversion,
  DesenlaceReversion,
  EstadoSolicitudReversion,
  SolicitudReversion,
} from '../domain/reversion.model';

type ReversionDto = components['schemas']['SolicitudReversionRespuesta'];

/** DTO generado -> modelo propio, mismo criterio que el resto del panel. */
export function aSolicitudReversion(dto: ReversionDto): SolicitudReversion {
  return {
    id: dto.id ?? '',
    solicitudId: dto.solicitudId ?? '',
    pedidoId: dto.pedidoId ?? '',
    causal: (dto.causal ?? 'FRAUDE') as CausalReversion,
    fechaDeNoticia: dto.fechaDeNoticia ?? '',
    radicadaEn: dto.radicadaEn ?? '',
    verdictoAlRadicar: (dto.verdictoAlRadicar ?? 'INDETERMINADO') as VerdictoPlazo,
    estado: (dto.estado ?? 'RADICADA') as EstadoSolicitudReversion,
    gestionadaEn: dto.gestionadaEn ?? null,
    gestionadaPor: dto.gestionadaPor ?? null,
    gestion: dto.gestion ?? null,
    desenlace: (dto.desenlace ?? null) as DesenlaceReversion | null,
    resueltaEn: dto.resueltaEn ?? null,
    reintegroId: dto.reintegroId ?? null,
  };
}
