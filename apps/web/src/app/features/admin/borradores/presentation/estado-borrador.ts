import { EstadoBorrador } from '../domain/borrador.model';

/** Contorno y no relleno, como la insignia de estado de la lista de productos. */
const CLASES_INSIGNIA =
  'inline-flex items-center rounded-completo border px-12 py-4 text-xs font-medio';

const CLASES_ESTADO_BORRADOR: Record<EstadoBorrador, string> = {
  EN_REVISION: 'border-ts-primario text-ts-primario',
  APROBADO: 'border-ts-exito text-ts-exito',
  RECHAZADO: 'border-ts-error text-ts-error',
  RENOVACION_APLICADA: 'border-ts-borde text-ts-texto-suave',
};

/**
 * En su propio archivo y no en la página de la lista: el detalle también la usa, y importarla
 * desde allí arrastraba el componente entero de la lista al bundle del detalle.
 */
export function clasesDeEstadoBorrador(estado: EstadoBorrador): string {
  return CLASES_INSIGNIA + ' ' + CLASES_ESTADO_BORRADOR[estado];
}
