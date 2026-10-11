import { InjectionToken } from '@angular/core';
import {
  FiltroLotes,
  LoteEliminado,
  LoteIngesta,
  LotesPaginados,
  OrdenIngesta,
  PublicacionCerrada,
  SubirExportacion,
} from './ingesta.model';

/**
 * Lo que el panel hace con las ingestas: subir una exportación y mirar cómo van los lotes.
 *
 * `subir` es una sola operación aunque por debajo sean tres viajes —pedir la URL firmada, subir el
 * archivo derecho al bucket y avisar al servidor que ya está—: la pantalla no tiene por qué saber
 * que el archivo nunca pasa por la API. Devuelve el lote recién creado, en `RECIBIDO`.
 */
export interface RepositorioIngestasAdmin {
  listar(filtro: FiltroLotes): Promise<LotesPaginados>;
  obtener(id: string): Promise<LoteIngesta>;
  subir(comando: SubirExportacion): Promise<LoteIngesta>;
  /** Borra la ingesta con sus borradores y los productos no publicados que salieron de ella. */
  eliminar(id: string): Promise<LoteEliminado>;
  /**
   * Pausar, reanudar, detener o reintentar las fallidas. Devuelve el lote como quedó: detener en
   * curso da `DETENIENDO`, reintentar da `RECIBIDO`.
   */
  ordenar(id: string, orden: OrdenIngesta): Promise<LoteIngesta>;
  /** Las publicaciones del lote que no dejaron borrador, en el orden del chat. */
  publicacionesCerradas(id: string): Promise<readonly PublicacionCerrada[]>;
}

export const REPOSITORIO_INGESTAS_ADMIN = new InjectionToken<RepositorioIngestasAdmin>(
  'RepositorioIngestasAdmin',
);
