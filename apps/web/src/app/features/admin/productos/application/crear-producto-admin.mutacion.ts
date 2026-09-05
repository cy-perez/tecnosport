import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { CrearProductoAdmin, ProductoAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

export function usarCrearProductoAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: CrearProductoAdmin): Promise<ProductoAdmin> => repositorio.crear(comando),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] }),
  }));
}
