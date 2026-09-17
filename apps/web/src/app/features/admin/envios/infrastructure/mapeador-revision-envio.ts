import type { components } from '@tecnosport/contratos';
import {
  AcuseDeRevision,
  BandejaDeRevision,
  EmisionResuelta,
  EmisionEnRevision,
  EstadoEmisionEnRevision,
  EstadoEnvioEnRevision,
  GuiaEnRevision,
} from '../domain/revision-envio.model';

type BandejaDto = components['schemas']['BandejaDeRevisionRespuesta'];
type GuiaDto = components['schemas']['GuiaEnRevisionRespuesta'];
type EmisionDto = components['schemas']['EmisionEnRevisionRespuesta'];
type AcuseDto = components['schemas']['AcuseDeRevisionRespuesta'];
type EmisionResueltaDto = components['schemas']['EmisionResueltaRespuesta'];

/**
 * DTO generado -> modelo propio, mismo criterio que `mapeador-atencion.ts`: los enums llegan como
 * `string` porque springdoc no los expone como union literal, y el backend garantiza que el valor
 * es exactamente el nombre del enum.
 */
export function aBandejaDeRevision(dto: BandejaDto): BandejaDeRevision {
  return {
    guias: (dto.guias ?? []).map(aGuiaEnRevision),
    emisiones: (dto.emisiones ?? []).map(aEmisionEnRevision),
  };
}

function aGuiaEnRevision(dto: GuiaDto): GuiaEnRevision {
  return {
    guiaId: dto.guiaId ?? '',
    numeroGuia: dto.numeroGuia ?? '',
    transportadora: dto.transportadora ?? '',
    pedidoId: dto.pedidoId ?? '',
    numeroPedido: dto.numeroPedido ?? '',
    estado: (dto.estado ?? 'EXCEPCION') as EstadoEnvioEnRevision,
    descripcion: dto.descripcion ?? null,
    ocurrioEn: dto.ocurrioEn ?? '',
    recibidoEn: dto.recibidoEn ?? '',
    revisadaEn: dto.revisadaEn ?? null,
  };
}

function aEmisionEnRevision(dto: EmisionDto): EmisionEnRevision {
  return {
    emisionId: dto.emisionId ?? '',
    pedidoId: dto.pedidoId ?? '',
    numeroPedido: dto.numeroPedido ?? '',
    transportadora: dto.transportadora ?? '',
    idTarifa: dto.idTarifa ?? '',
    estado: (dto.estado ?? 'INDETERMINADA') as EstadoEmisionEnRevision,
    detalle: dto.detalle ?? null,
    enviosEnPlataforma: dto.enviosEnPlataforma ?? [],
    solicitadaEn: dto.solicitadaEn ?? '',
    actor: dto.actor ?? '',
  };
}

export function aAcuseDeRevision(dto: AcuseDto): AcuseDeRevision {
  return {
    id: dto.id ?? '',
    tipo: (dto.tipo ?? 'GUIA') as 'GUIA' | 'EMISION',
    referencia: dto.referencia ?? '',
    revisadoEn: dto.revisadoEn ?? '',
    actor: dto.actor ?? '',
    nota: dto.nota ?? null,
  };
}

export function aEmisionResuelta(dto: EmisionResueltaDto): EmisionResuelta {
  return {
    emisionId: dto.emisionId ?? '',
    estado: dto.estado ?? '',
    detalle: dto.detalle ?? null,
    enviosEnPlataforma: dto.enviosEnPlataforma ?? [],
    resueltaEn: dto.resueltaEn ?? null,
  };
}
