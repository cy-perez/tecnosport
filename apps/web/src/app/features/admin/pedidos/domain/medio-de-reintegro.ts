import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { MetodoPago } from './pedido-admin.model';

/**
 * Por dónde se devuelve, por omisión: por donde entró. Se puede cambiar —los términos permiten "el
 * medio que acordemos contigo"—, pero el punto de partida no puede ser Wompi para todo: un pedido de
 * Sistecrédito devuelto "por Wompi" deja el crédito vivo y al comprador pagando cuotas.
 */
export const MEDIO_POR_METODO: Record<MetodoPago, MedioReintegro> = {
  WOMPI: 'WOMPI',
  SISTECREDITO: 'SISTECREDITO',
  TRANSFERENCIA_MANUAL: 'TRANSFERENCIA_BANCARIA',
  CONTRAENTREGA: 'EFECTIVO',
};

export const MEDIOS_DE_REINTEGRO: readonly MedioReintegro[] = [
  'WOMPI',
  'SISTECREDITO',
  'TRANSFERENCIA_BANCARIA',
  'EFECTIVO',
  'OTRO',
];
