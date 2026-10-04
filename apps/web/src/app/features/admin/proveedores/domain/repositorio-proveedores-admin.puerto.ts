import { InjectionToken } from '@angular/core';
import { DatosProveedor, Proveedor } from './proveedor.model';

/**
 * Lo que el panel puede hacer con los proveedores: verlos, dar de alta uno, corregirlo y
 * eliminarlo.
 *
 * Eliminar se lleva su historial de ingesta, y el servidor lo rechaza si algún producto del
 * catálogo salió de él: ahí la salida es desactivarlo, y la ingesta rechaza al inactivo.
 */
export interface RepositorioProveedoresAdmin {
  listar(): Promise<Proveedor[]>;
  obtener(id: string): Promise<Proveedor>;
  crear(datos: DatosProveedor): Promise<Proveedor>;
  editar(id: string, datos: DatosProveedor): Promise<Proveedor>;
  eliminar(id: string): Promise<void>;
}

export const REPOSITORIO_PROVEEDORES_ADMIN = new InjectionToken<RepositorioProveedoresAdmin>(
  'RepositorioProveedoresAdmin',
);
