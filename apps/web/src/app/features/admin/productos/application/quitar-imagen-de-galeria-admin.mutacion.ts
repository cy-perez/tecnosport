import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { QuitarImagenDeGaleriaAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

/**
 * Solo invalida el detalle del producto: la galería no sale en la lista ni en ninguna otra
 * consulta, al revés que publicar —que toca cuatro—.
 */
export function usarQuitarImagenDeGaleriaAdmin() {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (comando: QuitarImagenDeGaleriaAdmin): Promise<void> =>
      repositorio.quitarImagenDeGaleria(comando),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: ['admin', 'productos', variables.productoId] }),
  }));
}
