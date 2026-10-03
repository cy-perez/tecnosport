import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { AsignarColorAImagenAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/** Solo invalida el detalle del producto, como reordenar y quitar: la galería no sale en otra. */
export function usarAsignarColorAImagenAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: AsignarColorAImagenAdmin): Promise<void> =>
      repositorio.asignarColorAImagen(comando),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: ['admin', 'productos', variables.productoId] }),
  }));
}
