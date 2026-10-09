import { inject } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { LoteIngesta, OrdenIngesta } from '../domain/ingesta.model';
import { REPOSITORIO_INGESTAS_ADMIN } from '../domain/repositorio-ingestas-admin.puerto';
import { CLAVE_INGESTAS_ADMIN } from './listar-ingestas.consulta';

export interface ComandoOrdenIngesta {
  readonly id: string;
  readonly orden: OrdenIngesta;
}

/**
 * Pausar, reanudar o detener un lote. Solo se invalida la lista de lotes: ninguna de las tres
 * borra ni crea borradores por sí misma —lo que el lote alcance a hacer lo dirá el sondeo de la
 * lista, que sigue encendido mientras el lote esté abierto—.
 */
export function usarOrdenarIngesta() {
  const repositorio = inject(REPOSITORIO_INGESTAS_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: ({ id, orden }: ComandoOrdenIngesta): Promise<LoteIngesta> =>
      repositorio.ordenar(id, orden),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CLAVE_INGESTAS_ADMIN });
    },
  }));
}
