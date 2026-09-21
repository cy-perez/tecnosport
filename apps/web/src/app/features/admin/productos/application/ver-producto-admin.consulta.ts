import { inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ProductoAdminDetalle } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

export function usarVerProductoAdmin(id: () => string) {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);

  return injectQuery(() => ({
    queryKey: ['admin', 'productos', id()] as const,
    queryFn: (): Promise<ProductoAdminDetalle> => repositorio.obtener(id()),
    staleTime: 15_000,
  }));
}
