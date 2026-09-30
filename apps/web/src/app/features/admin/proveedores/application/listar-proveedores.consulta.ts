import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Proveedor } from '../domain/proveedor.model';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../domain/repositorio-proveedores-admin.puerto';

export const CLAVE_PROVEEDORES_ADMIN = ['admin', 'proveedores'] as const;

/**
 * Todos los proveedores, activos e inactivos: la lista los distingue y los desplegables de
 * ingestas y de borradores necesitan también a los inactivos, que siguen teniendo lotes y
 * borradores viejos que filtrar.
 */
export function usarProveedoresAdmin() {
  const repositorio = inject(REPOSITORIO_PROVEEDORES_ADMIN);
  return injectQuery(() => ({
    queryKey: CLAVE_PROVEEDORES_ADMIN,
    queryFn: (): Promise<Proveedor[]> => repositorio.listar(),
    // Son pocos y cambian poco; lo que importa es que quien acaba de crear uno lo vea.
    staleTime: 30_000,
  }));
}
