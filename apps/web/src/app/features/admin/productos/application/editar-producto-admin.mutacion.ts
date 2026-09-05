import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { EditarProductoAdmin, ProductoAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

export function usarEditarProductoAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (variables: { id: string; comando: EditarProductoAdmin }): Promise<ProductoAdmin> =>
      repositorio.editar(variables.id, variables.comando),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] }),
  }));
}
