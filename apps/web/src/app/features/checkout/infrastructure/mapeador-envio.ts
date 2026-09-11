import type { components } from '@tecnosport/contratos';
import { CotizacionEnvio } from '../domain/envio.model';

type CotizacionDto = components['schemas']['CotizacionEnvioRespuesta'];

/** DTO generado -> modelo propio del front, mismo criterio que `mapeador-pedido`. */
export function aCotizacionEnvio(dto: CotizacionDto): CotizacionEnvio {
  return {
    costoEnvio: dto.costoEnvio?.valor ?? 0,
    moneda: dto.costoEnvio?.moneda ?? 'COP',
    transportadora: dto.transportadora ?? '',
    diasEstimados: dto.diasEstimados ?? 0,
    venceEn: dto.venceEn ?? '',
    admiteContraentrega: dto.admiteContraentrega ?? false,
  };
}
