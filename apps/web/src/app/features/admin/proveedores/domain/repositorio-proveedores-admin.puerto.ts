import { InjectionToken } from '@angular/core';
import { DatosProveedor, Proveedor } from './proveedor.model';

/**
 * Lo que el panel puede hacer con los proveedores: verlos, dar de alta uno y corregirlo.
 *
 * Sin borrar: de un proveedor cuelgan lotes, mensajes, borradores y productos publicados. Para
 * dejar de recibirle, se desactiva, y la ingesta rechaza al inactivo.
 */
export interface RepositorioProveedoresAdmin {
  listar(): Promise<Proveedor[]>;
  obtener(id: string): Promise<Proveedor>;
  crear(datos: DatosProveedor): Promise<Proveedor>;
  editar(id: string, datos: DatosProveedor): Promise<Proveedor>;
}

export const REPOSITORIO_PROVEEDORES_ADMIN = new InjectionToken<RepositorioProveedoresAdmin>(
  'RepositorioProveedoresAdmin',
);
