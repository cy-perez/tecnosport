import { FotogramaCrudo } from './camara.puerto';
import { Inclinacion } from './nivel-360';

export type EstadoPermiso = 'DESCONOCIDO' | 'CONCEDIDO' | 'NEGADO' | 'NO_DISPONIBLE';

/** Un fotograma ya tomado, con la inclinación que tenía el teléfono en ese momento. */
export interface FotogramaCapturado {
  readonly orden: number;
  readonly imagen: FotogramaCrudo;
  readonly inclinacion: Inclinacion | null;
}

/**
 * Cuántos fotogramas puede tener un set, según la tabla de `docs/10-captura-360.md`. El backend
 * valida lo mismo (`SetRotacion.FOTOGRAMAS_MINIMOS`/`MAXIMOS`); esto es para no ofrecer en
 * pantalla algo que el servidor va a rechazar.
 */
export const FOTOGRAMAS_POSIBLES = [4, 8, 16] as const;

export const FOTOGRAMAS_RECOMENDADOS = 8;

/**
 * El nombre de cada toma. `docs/10-captura-360.md` fija los cuatro puntos cardinales en orden de
 * captura —frontal, lateral derecho, posterior, lateral izquierdo—; entre ellos no hay nombre que
 * inventar, así que va el ángulo. Deducir "derecho" o "izquierdo" de la geometría es justo el
 * tipo de detalle que se equivoca callado y confunde a quien está capturando.
 */
export function claveDeToma(orden: number, total: number): string {
  const grados = (orden * 360) / total;
  switch (grados) {
    case 0:
      return 'captura360.toma.frontal';
    case 90:
      return 'captura360.toma.lateral_derecho';
    case 180:
      return 'captura360.toma.posterior';
    case 270:
      return 'captura360.toma.lateral_izquierdo';
    default:
      return 'captura360.toma.angulo';
  }
}

export function gradosDeToma(orden: number, total: number): number {
  return total <= 0 ? 0 : (orden * 360) / total;
}
