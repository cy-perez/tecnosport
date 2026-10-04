import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { UsarImagenComoPrincipalAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/**
 * Invalida más que reordenar o el color: la principal sale en la lista del panel —la miniatura de
 * cada fila— y en la tarjeta y la ficha de la tienda, así que entran el prefijo del panel y el
 * catálogo público.
 */
export function usarUsarImagenComoPrincipalAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: UsarImagenComoPrincipalAdmin): Promise<void> =>
      repositorio.usarImagenComoPrincipal(comando),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
      await queryClient.invalidateQueries({ queryKey: ['catalogo'] });
    },
  }));
}
