import { InjectionToken } from '@angular/core';
import { Inclinacion } from './nivel-360';

/**
 * Una toma aceptada, guardada en disco del navegador mientras el set no se ha subido.
 *
 * `sesionId` es de la **sesión local**, no del set del backend: el set se abre recién al subir,
 * así que capturar funciona sin red — que es lo normal en la bodega de una tienda.
 */
export interface FotogramaGuardado {
  readonly sesionId: string;
  readonly orden: number;
  readonly blob: Blob;
  readonly ancho: number;
  readonly alto: number;
  readonly inclinacion: Inclinacion | null;
}

/** Lo que hace falta para retomar una captura donde se quedó. */
export interface SesionGuardada {
  readonly sesionId: string;
  readonly productoId: string;
  readonly fotogramasPrometidos: number;
  readonly objetivo: Inclinacion | null;
  readonly actualizadaEn: number;
}

/**
 * El set en curso vive en disco del navegador, no solo en memoria: cerrar la pestaña por
 * accidente no puede costar quince fotos (`docs/10-captura-360.md`).
 *
 * Tiene que ser IndexedDB y no `localStorage`: ocho fotos de dos megas no caben ahí, y además
 * `localStorage` solo guarda texto — un `Blob` habría que pasarlo a base64, que lo engorda un
 * tercio y bloquea el hilo principal mientras se convierte.
 *
 * **Subir cada toma apenas se acepta no habría servido de nada**: el factor de escala es común a
 * todo el set y sale del rectángulo más grande de todos los fotogramas, así que hasta que no
 * está la última toma no se puede procesar ninguna. Guardar en disco es lo único que protege el
 * trabajo mientras tanto.
 */
export interface AlmacenLocalDeCapturas {
  /** Falso en el servidor y en un navegador sin IndexedDB (o en modo privado de algunos). */
  disponible(): boolean;

  guardarSesion(sesion: SesionGuardada): Promise<void>;

  /** La sesión a medias de este producto, si quedó alguna. */
  sesionDe(productoId: string): Promise<SesionGuardada | null>;

  guardarFotograma(fotograma: FotogramaGuardado): Promise<void>;

  /** Los fotogramas guardados de un set, ordenados. */
  fotogramasDe(sesionId: string): Promise<FotogramaGuardado[]>;

  /** Borra la sesión y sus fotogramas: el set se subió, o se descartó a propósito. */
  olvidar(sesionId: string): Promise<void>;
}

export const ALMACEN_LOCAL_DE_CAPTURAS = new InjectionToken<AlmacenLocalDeCapturas>(
  'AlmacenLocalDeCapturas',
);
