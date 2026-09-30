import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Proveedor } from '../domain/proveedor.model';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../domain/repositorio-proveedores-admin.puerto';
import { CLAVE_PROVEEDORES_ADMIN } from './listar-proveedores.consulta';

export function usarVerProveedorAdmin(id: () => string) {
  const repositorio = inject(REPOSITORIO_PROVEEDORES_ADMIN);
  return injectQuery(() => ({
    queryKey: [...CLAVE_PROVEEDORES_ADMIN, id()] as const,
    queryFn: (): Promise<Proveedor> => repositorio.obtener(id()),
    enabled: id() !== '',
    staleTime: 30_000,
  }));
}
