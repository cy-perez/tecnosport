import { DeteccionDeRecorte, encuadreDelSet, Rectangulo } from '../domain/recorte-360';
import { ColorRgb } from '../domain/recorte-360';
import { ProcesadorDeFotogramas } from '../domain/procesador-fotogramas.puerto';
import { FotogramaCapturado } from '../domain/sesion-captura.model';

/** Un fotograma listo para subir: cuadrado, del lado de salida y en WebP. */
export interface FotogramaProcesado {
  readonly orden: number;
  readonly blob: Blob;
}

export type ResultadoDelProceso =
  | { readonly ok: true; readonly fotogramas: FotogramaProcesado[] }
  /** `orden` es el fotograma que no se pudo medir; `motivo` viene de `recorte-360`. */
  | { readonly ok: false; readonly orden: number; readonly motivo: string };

/**
 * Procesa el set entero: mide cada toma, calcula el encuadre **común** y recién entonces
 * renderiza. Ese orden no es negociable — si cada fotograma se escalara por su cuenta, el
 * producto crecería y encogería al girar, que es el defecto más visible de un 360 casero.
 *
 * Es también el motivo por el que no se puede subir sobre la marcha: hasta que no está la última
 * toma no se conoce la escala de ninguna.
 *
 * Va en el hilo principal, un fotograma a la vez y cediendo el turno entre uno y otro, para que
 * la barra de progreso se repinte. `docs/10-captura-360.md` pide un Web Worker; queda pendiente,
 * con su motivo, hasta medirlo en un teléfono real.
 */
export async function procesarSet(
  capturados: readonly FotogramaCapturado[],
  procesador: ProcesadorDeFotogramas,
  alAvanzar: (hechos: number, total: number) => void,
): Promise<ResultadoDelProceso> {
  const total = capturados.length * 2;
  const rectangulos: Rectangulo[] = [];
  const fondos: ColorRgb[] = [];

  for (const [indice, capturado] of capturados.entries()) {
    const deteccion: DeteccionDeRecorte = await procesador.medir(capturado.imagen.blob);
    if (!deteccion.ok) {
      return { ok: false, orden: capturado.orden, motivo: deteccion.motivo };
    }
    rectangulos.push(deteccion.rectangulo);
    fondos.push(deteccion.fondo);
    alAvanzar(indice + 1, total);
    await cederElTurno();
  }

  const encuadre = encuadreDelSet(rectangulos);
  if (encuadre === null) {
    return { ok: false, orden: 0, motivo: 'SIN_ENCUADRE' };
  }

  const fotogramas: FotogramaProcesado[] = [];
  for (const [indice, capturado] of capturados.entries()) {
    const blob = await procesador.renderizar(
      capturado.imagen.blob,
      rectangulos[indice],
      encuadre,
      fondos[indice],
    );
    fotogramas.push({ orden: capturado.orden, blob });
    alAvanzar(capturados.length + indice + 1, total);
    await cederElTurno();
  }

  return { ok: true, fotogramas };
}

/** Un turno real del bucle de eventos: sin esto la barra de progreso no llega a repintarse. */
function cederElTurno(): Promise<void> {
  return new Promise((resolver) => setTimeout(resolver, 0));
}
