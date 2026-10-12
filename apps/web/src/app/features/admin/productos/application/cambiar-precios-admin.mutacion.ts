import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { CambiarPreciosAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/**
 * Dos llaves, como al cambiar una talla: el detalle, donde se ve el precio nuevo, y el catálogo
 * público, cuya tarjeta y ficha lo enseñan y se quedarían con el viejo hasta caducar.
 */
export function usarCambiarPreciosAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: CambiarPreciosAdmin): Promise<void> =>
      repositorio.cambiarPrecios(comando),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['catalogo'] });
      return queryClient.invalidateQueries({
        queryKey: ['admin', 'productos', variables.productoId],
      });
    },
  }));
}
