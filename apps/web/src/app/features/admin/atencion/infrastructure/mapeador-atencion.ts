import type { components } from '@tecnosport/contratos';
import {
  EstadoSolicitudAtencion,
  Prorroga,
  RespuestaSolicitud,
  SolicitudAtencion,
  TipoSolicitud,
  VerdictoPlazo,
} from '../domain/atencion.model';

type SolicitudDto = components['schemas']['SolicitudAtencionRespuesta'];
type ProrrogaDto = components['schemas']['ProrrogaRespuesta'];
type RespuestaDto = components['schemas']['RespuestaRespuesta'];

/**
 * DTO generado -> modelo propio, mismo criterio que `mapeador-retracto.ts`: los enums llegan como
 * `string` en el contrato porque springdoc no los expone como union literal, y el backend garantiza
 * que el valor es exactamente el nombre del enum.
 */
export function aSolicitudAtencion(dto: SolicitudDto): SolicitudAtencion {
  return {
    id: dto.id ?? '',
    numeroRadicado: dto.numeroRadicado ?? '',
    tipo: (dto.tipo ?? 'PETICION') as TipoSolicitud,
    correo: dto.correo ?? '',
    pedidoId: dto.pedidoId ?? null,
    recibidaEn: dto.recibidaEn ?? '',
    radicadaEn: dto.radicadaEn ?? '',
    radicadaPor: dto.radicadaPor ?? '',
    asunto: dto.asunto ?? '',
    estado: (dto.estado ?? 'RADICADA') as EstadoSolicitudAtencion,
    limiteDeRespuesta: dto.limiteDeRespuesta ?? '',
    verdicto: (dto.verdicto ?? 'INDETERMINADO') as VerdictoPlazo,
    prorroga: dto.prorroga ? aProrroga(dto.prorroga) : null,
    respuesta: dto.respuesta ? aRespuesta(dto.respuesta) : null,
  };
}

function aProrroga(dto: ProrrogaDto): Prorroga {
  return {
    otorgadaEn: dto.otorgadaEn ?? '',
    otorgadaPor: dto.otorgadaPor ?? '',
    motivo: dto.motivo ?? '',
    avisadaEn: dto.avisadaEn ?? '',
  };
}

function aRespuesta(dto: RespuestaDto): RespuestaSolicitud {
  return {
    respondidaEn: dto.respondidaEn ?? '',
    respondidaPor: dto.respondidaPor ?? '',
    resumen: dto.resumen ?? '',
  };
}
