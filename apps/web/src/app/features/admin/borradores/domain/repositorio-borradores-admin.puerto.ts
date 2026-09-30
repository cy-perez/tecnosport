import { InjectionToken } from '@angular/core';
import {
  AprobarBorrador,
  Borrador,
  BorradorDetalle,
  BorradoresPaginados,
  EditarBorrador,
  FiltroBorradores,
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
}

export const REPOSITORIO_BORRADORES_ADMIN = new InjectionToken<RepositorioBorradoresAdmin>(
  'RepositorioBorradoresAdmin',
);
