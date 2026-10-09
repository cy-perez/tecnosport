import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { CambiarTallaAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/**
 * Dos llaves, como al ajustar una existencia: el detalle, donde se ve la talla nueva, y el
 * catálogo público, cuya ficha la enseña en el selector y se quedaría con la vieja hasta caducar.
 */
export function usarCambiarTallaAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: CambiarTallaAdmin): Promise<void> => repositorio.cambiarTalla(comando),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['catalogo'] });
      return queryClient.invalidateQueries({
        queryKey: ['admin', 'productos', variables.productoId],
      });
    },
  }));
}
