/**
 * El ciclo de un lote: se recibe la exportación, se procesa en segundo plano y termina bien o con
 * error. Mientras esté en los dos primeros, la lista se refresca sola.
 */
export type EstadoLote = 'RECIBIDO' | 'PROCESANDO' | 'TERMINADO' | 'ERROR';

export const ESTADOS_ABIERTOS: readonly EstadoLote[] = ['RECIBIDO', 'PROCESANDO'];

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
