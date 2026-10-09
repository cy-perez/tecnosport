/**
 * El ciclo de un lote: se recibe la exportación, se procesa en segundo plano y termina bien o con
 * error. Mientras se procesa se puede pausar —el hilo es uno solo y la cola espera con él— o
 * detener, y entonces `DETENIENDO` dura hasta que el servidor suelta el lote. Mientras esté abierto,
 * la lista se refresca sola.
 */
export type EstadoLote =
  'RECIBIDO' | 'PROCESANDO' | 'PAUSADO' | 'DETENIENDO' | 'TERMINADO' | 'DETENIDO' | 'ERROR';

export const ESTADOS_ABIERTOS: readonly EstadoLote[] = [
  'RECIBIDO',
  'PROCESANDO',
  'PAUSADO',
  'DETENIENDO',
];

/** Lo que el panel le puede pedir a un lote abierto. */
export type OrdenIngesta = 'pausar' | 'reanudar' | 'detener';

/** Las cifras con las que termina un lote. Todas en cero mientras corre. */
export interface ResumenIngesta {
  readonly mensajesLeidos: number;
  readonly mensajesNuevos: number;
  readonly mensajesIgnorados: number;
  readonly publicaciones: number;
  readonly borradoresNuevos: number;
  readonly renovaciones: number;
  readonly agotados: number;
  readonly descartes: number;
  readonly alertas: number;
}

export interface LoteIngesta {
  readonly id: string;
  readonly proveedorId: string;
  readonly estado: EstadoLote;
  /** ISO-8601, UTC. Se formatea en Bogotá al pintarlo. */
  readonly creadoEn: string;
  readonly iniciadoEn: string | null;
  readonly terminadoEn: string | null;
  readonly resumen: ResumenIngesta;
  /** Solo en `ERROR`: por qué no terminó, escrito para leerse. */
  readonly detalleError: string | null;
}

export interface LotesPaginados {
  readonly items: readonly LoteIngesta[];
  readonly pagina: number;
  readonly totalPaginas: number;
  readonly totalLotes: number;
}

export interface FiltroLotes {
  /** Vacío = todos los proveedores. */
  readonly proveedorId: string;
  readonly pagina: number;
}

/** El archivo que exporta WhatsApp: un `.zip` con el `.txt` del chat y las fotos. */
export interface SubirExportacion {
  readonly proveedorId: string;
  readonly archivo: File;
}

/** Lo que dejó borrar una ingesta: cuántos productos se fueron y cuántos se quedaron publicados. */
export interface LoteEliminado {
  readonly productosEliminados: number;
  readonly productosConservados: number;
}

export function loteAbierto(lote: LoteIngesta): boolean {
  return ESTADOS_ABIERTOS.includes(lote.estado);
}

/** En la cola no hay trabajo que pausar: el servidor solo pausa lo que se está procesando. */
export function sePuedePausar(lote: LoteIngesta): boolean {
  return lote.estado === 'PROCESANDO';
}

export function sePuedeReanudar(lote: LoteIngesta): boolean {
  return lote.estado === 'PAUSADO';
}

export function sePuedeDetener(lote: LoteIngesta): boolean {
  return lote.estado === 'RECIBIDO' || lote.estado === 'PROCESANDO' || lote.estado === 'PAUSADO';
}

/**
 * ¿Hay cifras que mostrar? Las de lo que terminó o falló, y las de lo que se detuvo después de
 * empezar. Uno detenido en la cola nunca se leyó: sus ceros no dicen nada.
 */
export function tieneResumen(lote: LoteIngesta): boolean {
  return (
    lote.estado === 'TERMINADO' ||
    lote.estado === 'ERROR' ||
    (lote.estado === 'DETENIDO' && lote.iniciadoEn !== null)
  );
}
