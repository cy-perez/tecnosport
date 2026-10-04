import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { LoteEliminado } from '../domain/ingesta.model';
import { REPOSITORIO_INGESTAS_ADMIN } from '../domain/repositorio-ingestas-admin.puerto';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';

/**
 * Borrar una ingesta se lleva sus borradores y sus productos no publicados, así que además de la
 * lista de lotes se invalidan la bandeja de borradores y la lista de productos del panel.
 */
export function usarEliminarIngesta() {
  const repositorio = inject(REPOSITORIO_INGESTAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<LoteEliminado> => repositorio.eliminar(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CLAVE_INGESTAS_ADMIN });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'borradores'] });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'productos'] });
    },
  }));
}
