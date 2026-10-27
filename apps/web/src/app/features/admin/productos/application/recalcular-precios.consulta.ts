import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { PrecioPorRecalcular } from '../domain/producto-admin.model';
import { REPOSITORIO_RECALCULO_PRECIOS } from '../domain/recalculo-precios.puerto';

/** Bajo `admin`, como el resto del panel; no cuelga de un producto porque los cruza todos. */
export const CLAVE_PRECIOS_POR_RECALCULAR = ['admin', 'precios', 'recalculo'] as const;

export function usarPreciosPorRecalcular() {
  const repositorio = inject(REPOSITORIO_RECALCULO_PRECIOS);

  return injectQuery(() => ({
    queryKey: CLAVE_PRECIOS_POR_RECALCULAR,
    queryFn: (): Promise<readonly PrecioPorRecalcular[]> => repositorio.previsualizar(),
    // Corto: la tarifa cambia con un despliegue, no mientras alguien mira, pero quien acaba de
    // cambiar un precio de contado en la ficha y vuelve aquí no debe ver la lista de antes.
    staleTime: 5_000,
  }));
}

/**
 * Aplica el recálculo. Invalida la previsualización —que tiene que quedar vacía—, los productos
 * del panel, cuyo publicado se enseña en la edición, y el catálogo público, cuya tarjeta y ficha
 * anuncian el publicado y se quedarían con el viejo hasta caducar.
 */
export function usarRecalcularPrecios() {
  const repositorio = inject(REPOSITORIO_RECALCULO_PRECIOS);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (): Promise<number> => repositorio.aplicar(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CLAVE_PRECIOS_POR_RECALCULAR });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
      await queryClient.invalidateQueries({ queryKey: ['catalogo'] });
    },
  }));
}
