import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { LoteEliminado } from '../domain/ingesta.model';
import { REPOSITORIO_INGESTAS_ADMIN } from '../domain/repositorio-ingestas-admin.puerto';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';

/**
 * Borrar una ingesta se lleva sus borradores y sus productos no publicados, así que se invalida el
 * panel entero y no solo la lista de lotes: la bandeja, los productos y las tres pantallas que
 * listan por variante —existencias, medidas, sin medir— enseñaban lo que ya no está.
 */
export function usarEliminarIngesta() {
  const repositorio = inject(REPOSITORIO_INGESTAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: (id: string): Promise<LoteEliminado> => repositorio.eliminar(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CLAVE_INGESTAS_ADMIN });
      await queryClient.invalidateQueries({ queryKey: ['admin'] });
    },
  }));
}
