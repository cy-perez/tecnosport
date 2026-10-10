import { inject } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { REPOSITORIO_BORRADORES_ADMIN } from '../domain/repositorio-borradores-admin.puerto';
import { CLAVE_BORRADORES_ADMIN } from './listar-borradores.consulta';

/**
 * Bajo el prefijo de borradores a propósito: toda decisión sobre un borrador —aprobar, rechazar,
 * borrar uno— ya invalida ese prefijo, y la cuenta tiene que moverse con ella.
 */
export const CLAVE_BORRADORES_SIN_APROBAR = [...CLAVE_BORRADORES_ADMIN, 'sin-aprobar'] as const;

/** Cuántos borraría el botón: se dice antes de confirmar, y sin ninguno el botón no aparece. */
export function usarContarBorradoresSinAprobar() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);

  return injectQuery(() => ({
    queryKey: CLAVE_BORRADORES_SIN_APROBAR,
    queryFn: (): Promise<number> => repositorio.contarSinAprobar(),
    staleTime: 15_000,
  }));
}

export interface BorrarSinAprobarComando {
  /** Después de cada tanda, para que la pantalla diga cuánto lleva. */
  readonly alAvanzar?: (borrados: number, quedan: number) => void;
}

/**
 * Pide tandas hasta que no quede ninguno y devuelve cuántos se borraron en total. Se detiene
 * también si una tanda no borra nada: los que quedan son de otro que los está decidiendo a la vez,
 * y repetir sería un ciclo sin fin.
 *
 * Invalida al terminar, también si falla a mitad: lo que alcanzó a borrarse ya no está.
 */
export function usarBorrarBorradoresSinAprobar() {
  const repositorio = inject(REPOSITORIO_BORRADORES_ADMIN);
  const queryClient = inject(QueryClient);

  return injectMutation(() => ({
    mutationFn: async (comando: BorrarSinAprobarComando = {}): Promise<number> => {
      let borrados = 0;
      for (;;) {
        const tanda = await repositorio.eliminarSinAprobar();
        borrados += tanda.eliminados;
        comando.alAvanzar?.(borrados, tanda.quedan);
        if (tanda.quedan === 0 || tanda.eliminados === 0) {
          return borrados;
        }
      }
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: CLAVE_BORRADORES_ADMIN }),
  }));
}
