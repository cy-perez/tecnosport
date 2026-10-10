import { InjectionToken } from '@angular/core';
import {
  AprobarBorrador,
  Borrador,
  BorradorDetalle,
  BorradoresPaginados,
  EditarBorrador,
  FiltroBorradores,
  FotoBorrador,
  TandaDeBorradoresEliminados,
} from './borrador.model';

/**
 * Lo que el panel hace con un borrador: mirarlo con sus fotos y sus textos, corregir lo que la
 * extracción sacó mal, y decidir. Aprobar crea el producto y devuelve el borrador ya `APROBADO`,
 * con el `productoId` puesto.
 */
export interface RepositorioBorradoresAdmin {
  listar(filtro: FiltroBorradores): Promise<BorradoresPaginados>;
  obtener(id: string): Promise<BorradorDetalle>;
  editar(id: string, cambios: EditarBorrador): Promise<Borrador>;
  aprobar(id: string, comando: AprobarBorrador): Promise<Borrador>;
  rechazar(id: string, motivo: string): Promise<Borrador>;
  /**
   * Saca una foto de la revisión. El archivo no se borra: es de la publicación, y otro borrador del
   * mismo mensaje puede usarlo.
   */
  descartarFoto(id: string, mensajeId: string): Promise<void>;
  /**
   * Sube una foto al borrador. Una sola operación aunque sean tres viajes —pedir la URL firmada,
   * subir el archivo al bucket y confirmarlo—, como la exportación de una ingesta. Devuelve la foto
   * ya colgada del borrador, con su `mensajeId` para tratarla como a las demás.
   */
  subirFoto(id: string, archivo: File): Promise<FotoBorrador>;
  /**
   * Parte el borrador: las fotos nombradas se van a un borrador nuevo de la misma publicación, con
   * los mismos datos, y en este quedan descartadas. Devuelve el nuevo. Para cuando la lectura de
   * fotos juntó dos productos en uno.
   */
  partir(id: string, fotos: readonly string[]): Promise<Borrador>;
  /** Sin vuelta atrás: el borrador, la publicación, los mensajes y las fotos del bucket. */
  eliminar(id: string): Promise<void>;
  /** Cuántos hay en revisión o rechazados: lo que borraría `eliminarSinAprobar`. */
  contarSinAprobar(): Promise<number>;
  /**
   * Una tanda del borrado en bloque de los que están en revisión o rechazados, cada uno con su
   * publicación, sus mensajes y sus fotos del bucket. Los aprobados no se tocan.
   */
  eliminarSinAprobar(): Promise<TandaDeBorradoresEliminados>;
}

export const REPOSITORIO_BORRADORES_ADMIN = new InjectionToken<RepositorioBorradoresAdmin>(
  'RepositorioBorradoresAdmin',
);
