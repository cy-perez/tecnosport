import type { components } from '@tecnosport/contratos';
import { IntentoDePago } from '../domain/intento-pago.model';

type IntentoDePagoDto = components['schemas']['IntentoDePagoRespuesta'];

/** DTO generado -> modelo propio del front. Ningún componente ve la forma de la respuesta HTTP. */
export function aIntentoDePago(dto: IntentoDePagoDto): IntentoDePago {
  return {
    referencia: dto.referencia ?? '',
    monto: { valor: dto.monto?.valor ?? 0, moneda: dto.monto?.moneda ?? 'COP' },
    firmaIntegridad: dto.firmaIntegridad ?? '',
    llavePublica: dto.llavePublica ?? '',
    ambiente: dto.ambiente ?? '',
  };
}
