import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { ReordenarGaleriaAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/**
 * Solo invalida el detalle del producto, igual que quitar y por lo mismo: la galería no sale en la
 * lista ni en ninguna otra consulta.
 */
export function usarReordenarGaleriaAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: ReordenarGaleriaAdmin): Promise<void> =>
      repositorio.reordenarGaleria(comando),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: ['admin', 'productos', variables.productoId] }),
  }));
}
