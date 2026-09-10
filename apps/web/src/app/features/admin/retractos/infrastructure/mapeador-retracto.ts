import type { components } from '@tecnosport/contratos';
import {
  EstadoSolicitudRetracto,
  MedioReembolso,
  Reembolso,
  SolicitudRetracto,
  VerdictoPlazo,
} from '../domain/retracto.model';

type SolicitudDto = components['schemas']['SolicitudRetractoRespuesta'];
type ReembolsoDto = components['schemas']['ReembolsoRespuesta'];

/**
 * DTO generado -> modelo propio, mismo criterio que `mapeador-pedido-admin.ts`: los enums llegan
 * como `string` en el contrato porque springdoc no los expone como unión literal, y el backend
 * garantiza que el valor es exactamente el nombre del enum.
 */
export function aSolicitudRetracto(dto: SolicitudDto): SolicitudRetracto {
  return {
    id: dto.id ?? '',
    pedidoId: dto.pedidoId ?? '',
    radicadaEn: dto.radicadaEn ?? '',
    radicadaPor: dto.radicadaPor ?? '',
    motivo: dto.motivo ?? null,
    verdictoAlRadicar: (dto.verdictoAlRadicar ?? 'INDETERMINADO') as VerdictoPlazo,
    estado: (dto.estado ?? 'RADICADA') as EstadoSolicitudRetracto,
    productoRecibidoEn: dto.productoRecibidoEn ?? null,
    limiteDeReintegro: dto.limiteDeReintegro ?? null,
    reembolso: dto.reembolso ? aReembolso(dto.reembolso) : null,
  };
}

function aReembolso(dto: ReembolsoDto): Reembolso {
  return {
    monto: dto.monto ?? 0,
    medio: (dto.medio ?? 'OTRO') as MedioReembolso,
    comprobante: dto.comprobante ?? null,
    registradoEn: dto.registradoEn ?? '',
    registradoPor: dto.registradoPor ?? '',
  };
}
